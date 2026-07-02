#@String(label="Host", value="omero-server.epfl.ch", persist=true) host
#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@String(label="Datasets ID", value=119273, required=false) datasetsId
#@String(label="TAG CONFIGURATION : ", visibility=MESSAGE, required=false) msg1
#@Boolean(label="Image name tags",value=true) imageTags
#@Boolean(label="Serie name tags",value=true) serieTags

/* 
 * This Fiji script automatically adds tags to images already stored in one or more OMERO datasets, 
 * based on the parsing of image names and/or serie names. 
 * Tokens extracted from the names are created as tags and linked to the corresponding images on OMERO. 
 * Purely numeric tokens are ignored. 
 * A CSV report and a Fiji log file are saved upon completion.
 * 
 * The name of the image is parsed with:  underscores / space / forward_slash / backward_slash
 * 
 * The serie name is pasred with:  underscores / space / forward_slash / backward_slash / comma.
 * Tokens are used as tags on OMERO.
 * If the name of the image is : 'tk1 tk2-tk3_tk4.tif [tk5_tk6, tk7_tk8]' then tags are : tk1 / tk2-tk3 / tk4 / tk5 / tk6
 / tk7 / tk8
 * 
 * 
 * Dependencies
 *  - Fiji update site OMERO 5.5-5.6
 *  - Fiji update site PTBIOP, with simple-omero-client
 * 
 * Author: Rémy Dornier, EPFL - PTBIOP 
 * Date: 2023.11.08
 * Version: 2.0.2
 * 
 * -----------------------------------------------------------------------------
 * Copyright (c) 2026 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
 * All rights reserved.
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
 * -----------------------------------------------------------------------------
 * 
 * History
 * - 2023.11.08 : First release --v1.0
 * - 2024.02.29 : Add support for the fluorescence VSI images from new Slide Scanner (i.e. split serie name with comma) --v1.1
 * - 2024.03.11 : Add support for multiple datasets --v2.0
 * - 2024.05.10 : Update logger, CSV file generation and token separator --v2.0.1
 * - 2025.09.10 : Save Fiji log window --v2.0.2
 * - 2025.09.10 : Adding host in UI --v2.0.2
 */


// global constants
hasFailed = false
hasSilentlyFailed = false
message = ""
tokenSeparator = " | "
csvSeparator = ","

IMG_NAME = "Image name"
IMG_ID = "Image Id"
DST_NAME = "Dataset name"
DST_ID = "Dataset Id"
STS = "Status"
TAG = "Tags"

// Connection to server
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
				IJLoggerError("OMERO", message, e)
				continue
			}
		
			IJLoggerInfo("*****************");
			
			// get images to process
			IJLoggerInfo("OMERO", "Read images from dataset '" + datasetWrapper.getName() + "' : " + datasetWrapper.getId())
			def imgWrapperList
			try{
				imgWrapperList = datasetWrapper.getImages(user_client)
			}catch(Exception e){
			    hasSilentlyFailed = true
				message = "An error occurred when reading images from '" + datasetWrapper.getName() + "' : " + datasetWrapper.getId()
				IJLoggerError("OMERO", message, e)
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
					imgSummaryMap.put(STS, "Failed")
	    			message = "Impossible to link tags to this image"
					IJLoggerError(imgWrapper.getName(), message, e)
					continue
				}
				transferSummary.add(imgSummaryMap)
					
				IJLoggerInfo("*****************");
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
		
		log += " :  " + filteredTags.join(tokenSeparator)
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
	def headerList = [DST_NAME, DST_ID, IMG_NAME, IMG_ID, TAG, STS]
	String header = headerList.join(csvSeparator)
	String statusOverallSummary = ""

	// get all summaries
	transferSummaryList.each{imgSummaryMap -> 
		def statusSummaryList = []
		//loop over the parameters
		headerList.each{outputParam->
			if(imgSummaryMap.containsKey(outputParam))
				statusSummaryList.add(imgSummaryMap.get(outputParam))
			else
				statusSummaryList.add("-")
		}
		
		statusOverallSummary += statusSummaryList.join(csvSeparator) + "\n"
	}
	
	String content = header + "\n"+statusOverallSummary
					
	// save the report
	def name = getCurrentDateAndHour() + "_AutoTag_on_images_from_dataset_"+datasetsId
	String path = System.getProperty("user.home") + File.separator + "Downloads"
	IJLoggerInfo("CSV report", "Saving the report as '"+name+".csv' in "+path+"....")
	writeCSVFile(path, name, content)	
	IJLoggerInfo("CSV report", "DONE!")
	
	// save the log window
    saveFijiLogWindow(path, name)
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
 * Saves the Log of Fiji
 */
def saveFijiLogWindow(path, name){
	// create the path locally
    String filePath = path.toString() + File.separator + name + "_logs.txt";

	// select the log window
    IJ.selectWindow("Log")
    
    // save it
	IJ.saveAs("Text", filePath);
}


/**
 * Logger methods
 */
def getErrorStackTraceAsString(Exception e){
    return Arrays.stream(e.getStackTrace()).map(StackTraceElement::toString).reduce("",(a, b)->a + "     at "+b+"\n");
}
def IJLoggerError(String message){
	IJ.log(getCurrentDateAndHour() + "   [ERROR]        "+message); 
}
def IJLoggerError(String title, String message){
	IJ.log(getCurrentDateAndHour() + "   [ERROR]        ["+title+"] -- "+message); 
}
def IJLoggerError(String title, String message, Exception e){
    IJLoggerError(title, message);
    IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e));
}
def IJLoggerError(String message, Exception e){
    IJLoggerError(message);
    IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e));
}
def IJLoggerWarn(String message){
	IJ.log(getCurrentDateAndHour() + "   [WARNING]    "+message); 
}
def IJLoggerWarn(String title, String message){
	IJ.log(getCurrentDateAndHour() + "   [WARNING]    ["+title+"] -- "+message); 
}
def IJLoggerInfo(String message){
	IJ.log(getCurrentDateAndHour() + "   [INFO]             "+message); 
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