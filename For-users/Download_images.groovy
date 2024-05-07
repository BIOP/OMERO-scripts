#@String(label="Username") USERNAME
#@String(label="Password", style='password' , persist=false) PASSWORD
#@String(label="Object to process", choices={"image","dataset","project","plate","screen"}) object_type
#@Long(label="ID") id
#@File(label="Choose the destination folder", style='directory') dir



/* = CODE DESCRIPTION =
 * This script downloads all images within the specified container.
 * In the output folder, the script will automatically download images in folder and sub-folders 
 * corresponding to the selected project/dataset/plate/screen, following the naming convention : "containerName_containerId"
 * 
 * Be careful : if the images on OMERO have the same name, they will be overwitten locally.
 * 
 * == INPUTS ==
 *  - credentials 
 *  - object type (cannot be well as all images are part of the same fileset)
 *  - id
 *  - output folder
 * 
 * == OUTPUTS ==	
 *  - Downloaded images
 *  - CSV report in the Download folder
 *  - Logs in the Fiji Log window
 * 
 * 
 * = DEPENDENCIES =
 *  - omero-ij-5.8.3-all.jar
 *  - simple-omero-client-5.18.0 https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = AUTHOR INFORMATION =
 * Code written by Rémy Dornier - EPFL - SV - PTECH - BIOP
 * 
 * = PROJECT INFORMATION =
 * date : 2024.05.06
 * version : v1.0
 * 
 * = HISTORY =
 * - 2024.05.06 : First release --v1.0 
 *  
 * = COPYRIGHT =
 * © All rights reserved. ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP), 2022
 */

/**
 * Main. Connect to OMERO, process images and disconnect from OMERO
 * 
 */

// Connection to server
host = "omero-server.epfl.ch"
port = 4064
Client user_client = new Client()

try{
	user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())
}catch(Exception e){
	IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e))
	def message = "Cannot connect to "+host+". Please check your credentials"
	JOptionPane.showMessageDialog(null, message, "ERROR", JOptionPane.ERROR_MESSAGE);
	return
}

// global variables for popup messages
hasFailed = false
hasSilentlyFailed = false
message = ""
fileSetList = []

// global keys for the summary report
IMG_NAME= "OMERO Image name"
IMG_ID = "OMERO Image Id"
PATH = "Parent folder"
STS = "Status"
FSET = "Fileset ID"
LOCAL_NAME = "Local Image name"


if (user_client.isConnected()){
	IJLoggerInfo("OMERO","Connected to "+host)
	List<Map<String, String>> transferSummary = new ArrayList<>()
	
	try{
		def start = new Date()
		
		// switch to correct container and starts processing
		switch (object_type){
			case "image":	
				transferSummary = processImage(user_client, user_client.getImage(id), dir)
				break	
			case "dataset":
				transferSummary = processDataset(user_client, user_client.getDataset(id), dir)
				break
			case "project":
				transferSummary = processProject(user_client, user_client.getProject(id), dir)
				break
			case "plate":
				transferSummary = processPlate(user_client, user_client.getPlate(id), dir)
				break
			case "screen":
				transferSummary = processScreen(user_client, user_client.getScreen(id), dir)
				break
		}
		
		IJLoggerInfo("OMERO", "Total download time "+((new Date().getTime() - start.getTime())/1000)+" s")
		
		// final message
		if(hasSilentlyFailed)
			message = "The script ended with some errors."
		else 
			message = "The list of images has been successfully created."
		
	}catch(Exception e){
		IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e))
		if(!hasFailed){
			hasFailed = true
			message = "An error has occurred. Please look at the logs and the report to know where the processing has failed."
		}
	}finally{
		// generate CSV report
		try{
			IJLoggerInfo("CSV report", "Generate the CSV report...")
			generateCSVReport(transferSummary)
		}catch(Exception e2){
			IJLoggerError(e2.toString(), "\n"+getErrorStackTraceAsString(e2))
			hasFailed = true
			message += " An error has occurred during csv report generation."
		}finally{
			// disconnect
			user_client.disconnect()
			IJLoggerInfo("OMERO","Disconnected from "+host)
			
			// print final popup
			if(!hasFailed) {
				message += " A CSV report has been created in your 'Downloads' folder."
				if(hasSilentlyFailed){
					JOptionPane.showMessageDialog(null, message, "The end", JOptionPane.WARNING_MESSAGE);
				}else{
					JOptionPane.showMessageDialog(null, message, "The end", JOptionPane.INFORMATION_MESSAGE);
				}
			}else{
				JOptionPane.showMessageDialog(null, message, "ERROR", JOptionPane.ERROR_MESSAGE);
			}
		}
	}
}else{
	message = "Not able to connect to "+host
	IJLoggerError("OMERO", message)
	JOptionPane.showMessageDialog(null, message, "ERROR", JOptionPane.ERROR_MESSAGE);
}
return


/**
 * Download an image from OMERO
 * 
 * inputs
 * 		user_client : OMERO client
 * 		image_wpr : OMERO image
 * 		dir : path to the destination folder
 * 
 * */
def downloadImage(user_client, imageWrapper, parentFolder){	
	Map<String, String> imageSummaryMap = new HashMap<>()
	imageSummaryMap.put(IMG_NAME, imageWrapper.getName())
	imageSummaryMap.put(IMG_ID, imageWrapper.getId())
	List<File> files = new ArrayList<>();
	
	try{
		// creates folders if needed
		if(parentFolder.exists() || parentFolder.mkdirs()){
			def imgData = imageWrapper.asDataObject()
			
			// check whether the image isp art of a fileset (to not download it multiple times)
			if(!imgData.isFSImage() || !fileSetList.contains(imgData.getFilesetId())){
				IJLoggerInfo("LOCAL","Downloading image '"+imageWrapper.getName() +" : "+imageWrapper.getId() +"' in '"+parentFolder.getAbsolutePath()+"'...")
		       
		       // download images
		       files = user_client.getGateway().getFacility(TransferFacility.class).downloadImage(user_client.getCtx(), parentFolder.getAbsolutePath(), imageWrapper.getId());
		        
		        // write summary
		        if(files != null && !files.isEmpty()){
		        	if(imgData.isFSImage()){
		        		def fileset = imgData.getFilesetId()
		        		fileSetList.add(fileset)
		        		imageSummaryMap.put(FSET, fileset)
		        	}
		        	imageSummaryMap.put(PATH, parentFolder.getAbsolutePath())
		        	imageSummaryMap.put(LOCAL_NAME, files.stream().map(File::getName).collect(Collectors.toList()).join(" ; "))
		        	imageSummaryMap.put(STS, "Downloaded")
		        }else{
		        	imageSummaryMap.put(STS, "Failed")
		        }
			}else{
				// not downloaded because fileset already downloded
				IJLoggerInfo("LOCAL","Image '"+imageWrapper.getName() +" : "+imageWrapper.getId() +"' already downloaded within the fileset")
				imageSummaryMap.put(STS, "Downloaded")
				imageSummaryMap.put(FSET, imgData.getFilesetId())
			}
		}
	    else {
	        IJLoggerWarn("LOCAL", "The following path does not exists : "+parentFolder.getAbsolutePath()+ ". Cannot download the image")
	        imageSummaryMap.put(STS, "Failed")
	    } 
	}catch(Exception e){
		hasSilentlyFailed = true
		message = "Impossible to download image "+imageWrapper.getName() +" : "+imageWrapper.getId()
    	IJLoggerError("OMERO", message)
    	IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e))
    	imageSummaryMap.put(STS, "Failed")
    }
    
    return imageSummaryMap
}

/**
 * Import all images from a dataset in Fiji
 * 
 * inputs
 * 		user_client : OMERO client
 * 		dataset_wpr : OMERO dataset
 * 
 * */
def processDataset(user_client, dataset_wpr, parentFolder){
	IJLoggerInfo("OMERO","Working on dataset "+dataset_wpr.getName() +" : "+dataset_wpr.getId())
	File datasetFolder = new File(parentFolder.getAbsolutePath() + File.separator + dataset_wpr.getName() + "_" + dataset_wpr.getId())
	
	List<Map<String, String>> transferSummary = []
	dataset_wpr.getImages(user_client).each{ img_wpr ->
		transferSummary.addAll(downloadImage(user_client, img_wpr, datasetFolder))
	}
	return transferSummary
}


/**
 * Import all images from a dataset in Fiji
 * 
 * inputs
 * 		user_client : OMERO client
 * 		dataset_wpr : OMERO dataset
 * 
 * */
def processProject(user_client, project_wpr, parentFolder){
	IJLoggerInfo("OMERO","Working on project "+project_wpr.getName() +" : "+project_wpr.getId())
	File projectFolder = new File(parentFolder.getAbsolutePath() + File.separator + project_wpr.getName() + "_" + project_wpr.getId())
	
	List<Map<String, String>> transferSummary = []
	project_wpr.getDatasets().each{ dataset_wpr ->
		transferSummary.addAll(processDataset(user_client , dataset_wpr, projectFolder))
	}
	return transferSummary
}

/**
 * Import all images from a dataset in Fiji
 * 
 * inputs
 * 		user_client : OMERO client
 * 		dataset_wpr : OMERO dataset
 * 
 * */
def processWell(user_client, well_wpr, parentFolder){
	IJLoggerInfo("OMERO","Working on well "+well_wpr.getName() +" : "+well_wpr.getId())
	
	List<Map<String, String>> transferSummary = []
	well_wpr.getWellSamples().each{ 
		transferSummary.addAll(downloadImage(user_client , it.getImage(), parentFolder))
	}
	return transferSummary
}

/**
 * Import all images from a plate in Fiji
 * 
 * inputs
 * 		user_client : OMERO client
 * 		plate_wpr : OMERO plate
 * 
 * */
def processPlate(user_client, plate_wpr, parentFolder){
	IJLoggerInfo("OMERO","Working on plate "+plate_wpr.getName() +" : "+plate_wpr.getId())
	File plateFolder = new File(parentFolder.getAbsolutePath() + File.separator + plate_wpr.getName() + "_" + plate_wpr.getId())
	
	List<Map<String, String>> transferSummary = []
	plate_wpr.getWells(user_client).each{well_wpr ->
		transferSummary.addAll(processWell(user_client, well_wpr, plateFolder))
	}	
	return transferSummary
}


/**
 * Import all images from a screen in Fiji
 * 
 * inputs
 * 		user_client : OMERO client
 * 		screen_wpr : OMERO screen
 * 
 * */
def processScreen(user_client, screen_wpr, parentFolder){
	IJLoggerInfo("OMERO","Working on screen "+screen_wpr.getName() +" : "+screen_wpr.getId())
	File screenFolder = new File(parentFolder.getAbsolutePath() + File.separator + screen_wpr.getName() + "_" + screen_wpr.getId())
	
	List<Map<String, String>> transferSummary = []
	screen_wpr.getPlates().each{ plate_wpr ->	
		transferSummary.addAll(processPlate(user_client, plate_wpr, screenFolder))
	} 
	return transferSummary
}

/**
 * Create the CSV report from all info collecting during the processing
 */
def generateCSVReport(transferSummaryList){
	// define the header
	def headerList = [IMG_NAME, IMG_ID, FSET, STS, PATH, LOCAL_NAME]
	String header = headerList.join(",")
	String statusOverallSummary = ""
	
	// get all summaries
	transferSummaryList.each{imgSummaryMap -> 
		String statusSummary = ""
		//loop over the parameters
		headerList.each{outputParam->
			if(imgSummaryMap.containsKey(outputParam))
				statusSummary += imgSummaryMap.get(outputParam)+","
			else
				statusSummary += "-,"
		}
		
		statusOverallSummary += statusSummary + "\n"
	}
	String content = header + "\n"+statusOverallSummary
					
	// save the report
	def name = getCurrentDateAndHour()+"_OMERO_Download_report"
	String path = System.getProperty("user.home") + File.separator +"Downloads"
	IJLoggerInfo("CSV report", "Saving the report as '"+name+".csv' in "+path+"....")
	writeCSVFile(path, name, content)	
	IJLoggerInfo("CSV report", "DONE!")
}



/**
 * Save a csv file in the given path, with the given name
 */
def writeCSVFile(path, name, fileContent){
	// create the file locally
    File file = new File(path.toString() + File.separator + name + ".csv");

    try (BufferedWriter buffer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
        buffer.write(fileContent);
	}catch(Exception e){
		throw e
	}
}


/**
 * Logger methods
 */
def getErrorStackTraceAsString(Exception e){
    return Arrays.stream(e.getStackTrace()).map(StackTraceElement::toString).reduce("",(a, b)->a + "     at "+b+"\n");
}
def IJLoggerError(String title, String message){
	IJ.log(getCurrentDateAndHour() + "   [ERROR]        ["+title+"] -- "+message); 
}
def IJLoggerWarn(String title, String message){
	IJ.log(getCurrentDateAndHour() + "   [WARNING]    ["+title+"] -- "+message); 
}
def IJLoggerInfo(String title, String message){
	IJ.log(getCurrentDateAndHour() + "   [INFO]             ["+title+"] -- "+message); 
}
def getCurrentDateAndHour(){
    LocalDateTime localDateTime = LocalDateTime.now();
    LocalTime localTime = localDateTime.toLocalTime();
    LocalDate localDate = localDateTime.toLocalDate();
    return ""+localDate.getYear()+
            (localDate.getMonthValue() < 10 ? "0"+localDate.getMonthValue():localDate.getMonthValue()) +
            (localDate.getDayOfMonth() < 10 ? "0"+localDate.getDayOfMonth():localDate.getDayOfMonth())+"-"+
            (localTime.getHour() < 10 ? "0"+localTime.getHour():localTime.getHour())+"h"+
            (localTime.getMinute() < 10 ? "0"+localTime.getMinute():localTime.getMinute())+"m"+
            (localTime.getSecond() < 10 ? "0"+localTime.getSecond():localTime.getSecond());
}


/*
 * imports  
 */
import fr.igred.omero.*
import omero.gateway.facility.TransferFacility
import ij.IJ;
import javax.swing.JOptionPane; 
import fr.igred.omero.repository.*
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.io.File
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors