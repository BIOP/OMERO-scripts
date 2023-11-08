#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@String(label="Object to process", choices={"image","dataset","project","well","plate","screen"}) object_type
#@Long(label="Object ID", value=119273) id
#@Boolean(label="Save as key-values") writeAsKVP


/* = CODE DESCRIPTION =
 * - You can specify the ID of an "image","dataset","project","well","plate","screen"
 * - Then, the script generates a csv file with the list of all images (name + id) with their parent folder name (dataset/project or well/plate/screen)
 * - Optionnally, it also saves parent folder name as key-values on OMERO.
 * 
 *                                          **** BE CAREFUL *******
 * It can happen that image/dataset/project names contain comas. This is the case for all images coming from a plate and maybe for some of your images.
 * To be csv-reading compatible, all comas are replaced by semi-columns.
 * To recover the correct name, open the csv file in excel. Select the column with image names.
 * Go on Home->Search and Find->Replace. Replace ";" by "," and save.
 * We strongly suggest you to remove all comas from your image names on OMERO (if possible) before running this script.
 * 
 * 
 * == INPUTS ==
 *  - credentials 
 *  - id
 *  - object type
 *  - True if you want to save the hierarchy as KVP
 * 
 * == OUTPUTS ==
 *  - create a csv file with the list of images contained inside the specfied object_type
 *  - key values on OMERO
 * 
 * = DEPENDENCIES =
 *  - Fiji update site OMERO 5.5-5.6
 *  - simple-omero-client-5.9.2 or later : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by Rémy Dornier, EPFL - SV -PTECH - BIOP 
 * 22.08.2022
 * version : v2.0
 * 
 * = COPYRIGHT =
 * © All rights reserved. ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP), 2022
 * 
 * Licensed under the BSD-3-Clause License:
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided 
 * that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer 
 *    in the documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products 
 *     derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, 
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, 
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF 
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * == HISTORY ==
 * - 2023.06.19 : Remove unnecessary imports --v1.1
 * - 2023.10.16 : Add popup message at the end and in the case of error --v1.2
 * - 2023.11.08 : Add standardized popup, update csv file and add IJ logs --v2.0
 */

/**
 * Main.
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

// global keys for the summary report
IMG_NAME= "Image name"
IMG_ID = "Image Id"
DST_NAME = "Dataset name"
PRJ_NAME = "Project name"
WELL_NAME = "Well name"
PLT_NAME = "Plate name"
SCR_NAME = "Screen name"
KVP = "Key values"

if (user_client.isConnected()){
	IJLoggerInfo("OMERO","Connected to "+host)
	List<Map<String, String>> transferSummary = new ArrayList<>()
	
	try{
		switch (object_type){
			case "image":	
				transferSummary = processImage(user_client, user_client.getImage(id))
				break	
			case "dataset":
				transferSummary = processDataset(user_client, user_client.getDataset(id))
				break
			case "project":
				transferSummary = processProject(user_client, user_client.getProject(id))
				break
			case "well":
				transferSummary = processWell(user_client, user_client.getWells(id))
				break
			case "plate":
				transferSummary = processPlate(user_client, user_client.getPlates(id))
				break
			case "screen":
				transferSummary = processScreen(user_client, user_client.getScreens(id))
				break
		}
		
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


/**
 * List the full arborescence of an image in a csv format
 * 
 * inputs
 * 		user_client : OMERO client
 * 		image_wpr : OMERO image
 * 
 * */
def processImage(user_client, image_wpr){
	IJLoggerInfo("OMERO","Working on image "+image_wpr.getName() +" : "+image_wpr.getId())
	
	Map<String, String> summaryMap = new HashMap<>()
	summaryMap.put(IMG_NAME, image_wpr.getName().replaceAll(",",";"))
	summaryMap.put(IMG_ID, image_wpr.getId())
		
	// if the image is part of a dataset
	def dataset_wpr_list = image_wpr.getDatasets(user_client)
	if(!dataset_wpr_list.isEmpty()){
		def dataset_name =  dataset_wpr_list.get(0).getName()
		summaryMap.put(DST_NAME, dataset_name.replaceAll(",",";"))
		
		def project_wpr_list = image_wpr.getProjects(user_client)
		def isOrphanedDataset = false
		def project_name
		
		if(!project_wpr_list.isEmpty()){
			project_name =  project_wpr_list.get(0).getName()
			summaryMap.put(PRJ_NAME, project_name.replaceAll(",",";"))
		}else{
			isOrphanedDataset = true
		}
		
		// add key value pairs
		if(writeAsKVP){
			List<NamedValue> keyValues = new ArrayList()
	   		keyValues.add(new NamedValue("Dataset", dataset_name)) 
	   		if(!isOrphanedDataset)
	   			keyValues.add(new NamedValue("Project", project_name)) 
	   		
	   		try{
				addKeyValuetoOMERO(user_client, image_wpr, keyValues)
				summaryMap.put(KVP, "Yes")
	   		}catch(Exception e){
	   			hasSilentlyFailed = true
				message = "Key values cannot be attached to image '"+image_wpr.getName() +"':"+image_wpr.getId()						
				IJLoggerError("OMERO", message)
				IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e))
	   			summaryMap.put(KVP, "Failed")
	   		}
	   		
		}else{
			summaryMap.put(KVP, "-")
		}
	}
	
	// if the image is part of a plate
	else {
		def well_name =  image_wpr.getWells(user_client).get(0).getName()
		def plate_name = image_wpr.getPlates(user_client).get(0).getName()
		summaryMap.put(WELL_NAME, well_name.replaceAll(",",";"))
		summaryMap.put(PLT_NAME, plate_name.replaceAll(",",";"))
		
		def screen_wpr_list = image_wpr.getScreens(user_client)
		def isOrphanedPlate = false
		def screen_name
		
		if(!screen_wpr_list.isEmpty()){
			screen_name = screen_wpr_list.get(0).getName()
			summaryMap.put(SCR_NAME, screen_name.replaceAll(",",";"))
		}else{
			isOrphanedPlate = true
		}
		
		// add key-value pairs
		if(writeAsKVP){
			List<NamedValue> keyValues = new ArrayList()
	   		keyValues.add(new NamedValue("Well", well_name)) 
	   		keyValues.add(new NamedValue("Plate", plate_name)) 
	   		if(!isOrphanedPlate)
	   			keyValues.add(new NamedValue("Screen", screen_name)) 
	   		
			try{
				addKeyValuetoOMERO(user_client, image_wpr, keyValues)
				summaryMap.put(KVP, "Yes")
	   		}catch(Exception e){
	   			hasSilentlyFailed = true
				message = "Key values cannot be attached to image '"+image_wpr.getName() +"':"+image_wpr.getId()						
				IJLoggerError("OMERO", message)
				IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e))
	   			summaryMap.put(KVP, "Failed")
	   		}
		}
	}
	
	return summaryMap
}



/**
 * Add the key value to OMERO attach to the current repository wrapper
 * 
 * inputs
 * 		user_client : OMERO client
 * 		repository_wpr : OMERO repository object (image, dataset, project, well, plate, screen)
 * 
 * */
def addKeyValuetoOMERO(user_client, repository_wpr, keyValues){
	MapAnnotationWrapper newKeyValues = new MapAnnotationWrapper(keyValues)
	newKeyValues.setNameSpace("openmicroscopy.org/omero/client/mapAnnotation")
	repository_wpr.addMapAnnotation(user_client, newKeyValues)
}



/**
 * Create the CSV report from all info cleecting during the processing
 */
def generateCSVReport(transferSummaryList){
	String statusOverallSummary = ""
	
	// define the header
	String header = IMG_NAME + "," + IMG_ID + "," + DST_NAME + "," + PRJ_NAME + 
					"," + WELL_NAME + "," + PLT_NAME + "," + SCR_NAME + "," + KVP
	
	transferSummaryList.each{imgSummaryMap -> 
		String statusSummary = ""
	
		statusSummary += imgSummaryMap.get(IMG_NAME)+","
		statusSummary += imgSummaryMap.get(IMG_ID)+","
		
		// dataset
		if(imgSummaryMap.containsKey(DST_NAME))
			statusSummary += imgSummaryMap.get(DST_NAME)+","
		else
			statusSummary += " - ,"

		// project
		if(imgSummaryMap.containsKey(PRJ_NAME))
			statusSummary += imgSummaryMap.get(PRJ_NAME)+","
		else
			statusSummary += " - ,"
			
		// well
		if(imgSummaryMap.containsKey(WELL_NAME))
			statusSummary += imgSummaryMap.get(WELL_NAME)+","
		else
			statusSummary += " - ,"
			
		// plate
		if(imgSummaryMap.containsKey(PLT_NAME))
			statusSummary += imgSummaryMap.get(PLT_NAME)+","
		else
			statusSummary += " - ,"
		
		// screen
		if(imgSummaryMap.containsKey(SCR_NAME))
			statusSummary += imgSummaryMap.get(SCR_NAME)+","
		else
			statusSummary += " - ,"
		
		statusSummary += imgSummaryMap.get(KVP)
		
		statusOverallSummary += statusSummary + "\n"
	}

	String content = header + "\n"+statusOverallSummary
					
	// save the report
	def name = getCurrentDateAndHour()+"_Images_in_" + object_type + "_"+id
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
 * Import all images from a dataset in Fiji
 * 
 * inputs
 * 		user_client : OMERO client
 * 		dataset_wpr : OMERO dataset
 * 
 * */
def processDataset( user_client, dataset_wpr ){
	IJLoggerInfo("OMERO","Working on dataset "+dataset_wpr.getName() +" : "+dataset_wpr.getId())
	List<Map<String, String>> transferSummary = []
	dataset_wpr.getImages(user_client).each{ img_wpr ->
		transferSummary.add(processImage(user_client , img_wpr))
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
def processProject( user_client, project_wpr ){
	IJLoggerInfo("OMERO","Working on project "+project_wpr.getName() +" : "+project_wpr.getId())
	List<Map<String, String>> transferSummary = []
	project_wpr.getDatasets().each{ dataset_wpr ->
		transferSummary.addAll(processDataset(user_client , dataset_wpr))
	}
	return transferSummary
}


/**
 * Import all images from a well in Fiji
 * 
 * inputs
 * 		user_client : OMERO client
 * 		well_wpr_list : OMERO wells
 * 
 * */
def processWell(user_client, well_wpr_list){
	List<Map<String, String>> transferSummary = []
	well_wpr_list.each{ well_wpr ->			
		IJLoggerInfo("OMERO","Working on well "+well_wpr.getName() +" : "+well_wpr.getId())
		well_wpr.getWellSamples().each{			
			transferSummary.add(processImage(user_client, it.getImage()))
		}
	}	
	return transferSummary
}


/**
 * Import all images from a plate in Fiji
 * 
 * inputs
 * 		user_client : OMERO client
 * 		plate_wpr_list : OMERO plates
 * 
 * */
def processPlate(user_client, plate_wpr_list){
	List<Map<String, String>> transferSummary = []
	plate_wpr_list.each{ plate_wpr ->	
		IJLoggerInfo("OMERO","Working on plate "+plate_wpr.getName() +" : "+plate_wpr.getId())
		transferSummary.addAll(processWell(user_client, plate_wpr.getWells(user_client)))
	} 
	return transferSummary
}


/**
 * Import all images from a screen in Fiji
 * 
 * inputs
 * 		user_client : OMERO client
 * 		screen_wpr_List : OMERO screens
 * 
 * */
def processScreen(user_client, screen_wpr_list){
	List<Map<String, String>> transferSummary = []
	screen_wpr_list.each{ screen_wpr ->	
		IJLoggerInfo("OMERO","Working on screen "+screen_wpr.getName() +" : "+screen_wpr.getId())
		transferSummary.addAll(processPlate(user_client, screen_wpr.getPlates()))
	} 
	return transferSummary
}


/**
 * Logger methods
 */
def getErrorStackTraceAsString(Exception e){
    return Arrays.stream(e.getStackTrace()).map(StackTraceElement::toString).reduce("",(a, b)->a + "     at "+b+"\n");
}
def IJLoggerError(String title, String message){
	IJ.log("[ERROR]   ["+title+"] -- "+message); 
}
def IJLoggerWarn(String title, String message){
	IJ.log("[WARNING]   ["+title+"] -- "+message); 
}
def IJLoggerInfo(String title, String message){
	IJ.log("[INFO]   ["+title+"] -- "+message); 
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
import fr.igred.omero.annotations.*
import omero.model.NamedValue
import java.io.FileWriter
import java.io.File
import java.nio.charset.StandardCharsets;
import ij.IJ;
import javax.swing.JOptionPane;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;