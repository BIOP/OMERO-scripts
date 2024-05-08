#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@String(label="Datasets ID", value=119273, required=false) datasetsId
#@String(label="TAG CONFIGURATION : ", visibility=MESSAGE, required=false) msg1
#@Boolean(label="Image name tags",value=true) imageTags
#@Boolean(label="Serie name tags",value=true) serieTags

/* == CODE DESCRIPTION ==
 * This script batch tag images on OMERO, taking into account the name of the image and the name of the serie.
 * However, it doesn't take into account the original import path, like the Autotag plugin of OMERO.web does.
 * 
 * The name of the image is parsed with:  underscores / space / forward_slash / backward_slash
.
 * The serie name is pasred with:  underscores / space / forward_slash / backward_slash / comma.
 * Tokens are used as tags on OMERO.
 * If the name of the image is : 'tk1 tk2-tk3_tk4.tif [tk5_tk6, tk7_tk8]' then tags are : tk1 / tk2-tk3 / tk4 / tk5 / tk6
 / tk7 / tk8
 * 
 * The script automatically generates a CSV report that summarizes which image has been tagged, with which tags
 * The CSV report is saved in your Downloads folder.
 * 		
 * == INPUTS ==
 *  - credentials 
 *  - enter the ID of one or more dataset
s. If multiple dataset, separate them with ONLY a semi-colon ;
 * 	- You can configure the tags you want to add to your images on OMERO
 * 		- Image tags : onyl image name (without serie name) is parsed
 * 		- Serie name : the name of the serie is parsed
 * 	
 * 
 * == OUTPUTS ==	
 *  - Tags on OMERO
 *  - CSV report in the Download folder
 * 
 * 
 * = DEPENDENCIES =
 *  - Fiji update site OMERO 5.5-5.6
 *  - simple-omero-client-5.15.0 https://github.com/GReD-Clermont/simple-omero-client
 *  
 *  
 *  = AUTHOR INFORMATION =
 * Code written by Rémy Dornier - EPFL - SV - PTECH - BIOP
 * date : 2023.11.08
 * version : v2.0
 * 
 * = HISTORY =
 * - 2023.11.08 : First release --v1.0
 * - 2024.02.29 : Add support for the fluorescence VSI images from new Slide Scanner (i.e. split serie name with comma) --v1.1
 * - 2024.03.11 : Add support for multiple datasets --v2.0
 */


// global constants
hasFailed = false
hasSilentlyFailed = false
message = ""

IMG_NAME = "Image name"
IMG_ID = "Image Id"
DST_NAME = "Dataset name"
DST_ID = "Dataset Id"
STS = "Status"
TAG = "Tags"

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

if (user_client.isConnected()){
	IJLoggerInfo("OMERO","Connected to "+host)
	List<Map<String, String>> transferSummary = new ArrayList<>()
	
	
	try{		
		def datasetIDList = datasetsId.split(";") as List
		for(String datasetId : datasetIDList){
			// get the dataset
			def datasetWrapper
			try{
				IJLoggerInfo("OMERO","Getting dataset "+datasetId)
				datasetWrapper = user_client.getDataset(Long.parseLong(datasetId)) 
			}catch(Exception e){
				hasSilentlyFailed = true
				message = "The dataset '"+datasetId+"' cannot be found on OMERO."
				IJLoggerError("OMERO", message)
				IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e))
				continue
			}
		
			IJLoggerInfo("","*****************");
			
			// get images to process
			IJLoggerInfo("OMERO", "Read images from dataset '" + datasetWrapper.getName() + "' : " + datasetWrapper.getId())
			def imgWrapperList
			try{
				imgWrapperList = datasetWrapper.getImages(user_client)
			}catch(Exception e){
			    hasSilentlyFailed = true
				message = "An error occurred when reading images from '" + datasetWrapper.getName() + "' : " + datasetWrapper.getId()
				IJLoggerError("OMERO", message)
				IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e))
				continue
			}
			
			// link tags
			for(ImageWrapper imgWrapper : imgWrapperList){
				Map<String, String> imgSummaryMap = new HashMap<>()
				imgSummaryMap.put(IMG_ID, imgWrapper.getId())
				imgSummaryMap.put(IMG_NAME, imgWrapper.getName())
				imgSummaryMap.put(DST_ID, datasetWrapper.getId())
				imgSummaryMap.put(DST_NAME, datasetWrapper.getName())
				
				try{
					IJLoggerInfo(imgWrapper.getName(),"Parse image name and link tags")
					def tags = linkTagsToImage(user_client, imgWrapper)
					imgSummaryMap.put(TAG, tags.join(" ; "))
					imgSummaryMap.put(STS, "Added")
				}catch(Exception e){
					hasSilentlyFailed = true
	    			message = "Impossible to link tags to this image"
					IJLoggerError(imgWrapper.getName(), message)
					IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e))
					continue
				}
				transferSummary.add(imgSummaryMap)
					
				IJLoggerInfo("", "*****************");
			}
		}
		if(hasSilentlyFailed)
			message = "The script ended with some errors."
		else 
			message = "The tagging have been successfully done."

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
 * Add a list of tags to the specified image
 * 
 */
def saveTagsOnOmero(tags, imgWpr, user_client){
	def tagsToAdd = []
	
	// get existing tags
	def groupTags = user_client.getTags()
	def imageTags = imgWpr.getTags(user_client)
	
	// find if the tag to add already exists on OMERO. If yes, they are not added twice
	tags.each{tag->
		if(tagsToAdd.find{ it.getName().toLowerCase().equals(tag.toLowerCase()) } == null){
			// find if the requested tag already exists
			new_tag = groupTags.find{ it.getName().toLowerCase().equals(tag.toLowerCase()) } ?: new TagAnnotationWrapper(new TagAnnotationData(tag))
			
			// add the tag if it is not already the case
			imageTags.find{ it.getName().toLowerCase().equals(new_tag.getName().toLowerCase()) } ?: tagsToAdd.add(new_tag)
		}
		
	}
	
	// add all tags to the image
	imgWpr.addTags(user_client, (TagAnnotationWrapper[])tagsToAdd.toArray())
}


/**
 * upload images, parse the image name and add tags on OMERO
 */
def linkTagsToImage(user_client, imgWrapper){
	def patternSplitExtension = /\.[a-zA-Z0-9]*/
	def patternName = /[_ \/]/
	def patternSerieName = /[_, \/]/
	def patternSerie = /.*\[(?<serie>.*)\]/
	
	def nameToParse = imgWrapper.getName().split(patternSplitExtension)[0]
	def filteredTags = []
	
	if(serieTags || imageTags){
		String log = ""
		log += "Create tags from : "
		def omeImgName = imgWrapper.getName()
		def tags = []
		
		if(imageTags){
			log += "image name, "
			tags = nameToParse.split(patternName) as List
		}
		
		if(serieTags){
			log += "serie name, "
			// define the regex
			def regex = Pattern.compile(patternSerie)
			def matcher = regex.matcher(omeImgName)
			
			if(matcher.find()){
				def serieName = matcher.group("serie")
				tags.addAll(serieName.split(patternSerieName) as List)
			}
		}
		
		// remove tags that are only numbers
		tags.each{tag->
			try{
				Integer.parseInt(tag)
			}catch(Exception e){
				filteredTags.add(tag)
			}
		}
		
		log += " :  " + filteredTags.join(" ; ")
		saveTagsOnOmero(filteredTags.findAll(), imgWrapper, user_client)
		log += "...... Done !"
		IJLoggerInfo(imgWrapper.getName(), log)
	}
	
	return filteredTags.unique()
}



/**
 * Create the CSV report from all info cleecting during the processing
 */
def generateCSVReport(transferSummaryList){
	// define the header
	String header = DST_NAME + "," + DST_ID + "," + IMG_NAME + "," + IMG_ID + "," + TAG + "," + STS

	String statusOverallSummary = ""

	transferSummaryList.each{imgSummaryMap -> 
		String statusSummary = ""
		
		// Source image
		statusSummary += imgSummaryMap.get(DST_NAME)+","
		statusSummary += imgSummaryMap.get(DST_ID)+","
		statusSummary += imgSummaryMap.get(IMG_NAME)+","
		statusSummary += imgSummaryMap.get(IMG_ID)+","
		
		// tags
		if(imgSummaryMap.containsKey(TAG))
			statusSummary += imgSummaryMap.get(TAG)+","
		else
			statusSummary += " - ,"

		// Status
		if(imgSummaryMap.containsKey(STS))
			statusSummary += imgSummaryMap.get(STS)+","
		else
			statusSummary += "Failed"
		
		statusOverallSummary += statusSummary + "\n"
	}
	String content = header + "\n"+statusOverallSummary
					
	// save the report
	def name = getCurrentDateAndHour() + "_AutoTag_on_images_from_dataset_"+datasetsId
	String path = System.getProperty("user.home") + File.separator + "Downloads"
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
import javax.swing.JOptionPane;
import javax.swing.JFrame;
import fr.igred.omero.*
import fr.igred.omero.repository.*
import fr.igred.omero.annotations.*
import omero.gateway.model.DatasetData
import omero.gateway.model.ProjectData
import omero.gateway.model.TagAnnotationData;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JOptionPane; 
import java.nio.charset.StandardCharsets;
import ij.IJ;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;