#@String(label="Host", value="omero-server.epfl.ch", persist=true) host
#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@String(label="Object to process", choices={"image","dataset","project","well","plate","screen"}) object_type
#@Long(label="Object ID", value=119273) id


/* = CODE DESCRIPTION =
 * The code loops over all images, extracts channel names and create a CSV file saved in the Downloads.
 * The CSV file contains
 * - Image name
 * - Image ID
 * - All the channel names for the current image.
 * 
 * == INPUTS ==
 *  - host
 *  - credentials 
 *  - object type
 *  - id
 * 
 * == OUTPUTS ==
 *  - CSV file in the Downloads folder
 * 
 * = DEPENDENCIES =
 *  - omero_ij-5.8.6-all.jar : https://www.openmicroscopy.org/omero/downloads/
 *  - simple-omero-client-5.19.0 or later : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by Rémy Dornier, EPFL - SV - PTECH - BIOP 
 * version: 1.0.1
 * 
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
 * = HISTORY = 
 * - 2025.09.10: Save Fiji log window --v1.0.1
 */

/**
 * Main. Connect to OMERO, process images and disconnect from OMERO
 * 
 */
 
// Global variables
IMG_NAME = "Image Name"
IMG_ID = "Image ID"
CH_NAME = "Channel Name"

hasFailed = false
hasSilentlyFailed = false
message = ""
tokenSeparator = " | "
csvSeparator = ","

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
		switch (object_type){
			case "image":	
				transferSummary.addAll(processImage(user_client, user_client.getImage(id)))
				break	
			case "dataset":
				transferSummary.addAll(processDataset(user_client, user_client.getDataset(id)))
				break
			case "project":
				transferSummary.addAll(processProject(user_client, user_client.getProject(id)))
				break
			case "well":
				transferSummary.addAll(processWell(user_client, user_client.getWells(id)))
				break
			case "plate":
				transferSummary.addAll(processPlate(user_client, user_client.getPlates(id)))
				break
			case "screen":
				transferSummary.addAll(processScreen(user_client, user_client.getScreens(id)))
				break
		}
		
		// final message
		if(hasSilentlyFailed)
			message = "The script ended with some errors."
		else 
			message = "The list of channels per image has been successfully created."
		
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
 * process one image at a time
 * 
 * inputs
 * 		user_client : OMERO client
 * 		image_wpr : OMERO image
 * 
 * */
def processImage(user_client, image_wpr){
	List<Map<String, String>> transferSummary = new ArrayList<>()
	Map<String, String> imgSummaryMap = new HashMap<>()
	imgSummaryMap.put(IMG_NAME, image_wpr.getName())
	imgSummaryMap.put(IMG_ID, image_wpr.getId())
	
	user_client.getMetadata().getChannelData(user_client.getCtx(), image_wpr.getId()).each{
		imgSummaryMap.put(CH_NAME, it.getName())
		transferSummary.add(imgSummaryMap)
		imgSummaryMap = new HashMap<>()
	}
	
	return transferSummary
}



/**
 * Loop over images from a dataset
 * 
 * inputs
 * 		user_client : OMERO client
 * 		dataset_wpr : OMERO dataset
 * 
 * */
def processDataset( user_client, dataset_wpr ){
	IJLoggerInfo("OMERO","Working on dataset "+dataset_wpr.getName() +" : "+dataset_wpr.getId())
	List<Map<String, String>> transferSummary = new ArrayList<>()
	dataset_wpr.getImages(user_client).each{ img_wpr ->
		transferSummary.addAll(processImage(user_client , img_wpr))
	}
	return transferSummary
}


/**
 *  * Loop over dataset from a project
 * 
 * inputs
 * 		user_client : OMERO client
 * 		dataset_wpr : OMERO dataset
 * 
 * */
def processProject( user_client, project_wpr ){
	IJLoggerInfo("OMERO","Working on project "+project_wpr.getName() +" : "+project_wpr.getId())
	List<Map<String, String>> transferSummary = new ArrayList<>()
	project_wpr.getDatasets().each{ dataset_wpr ->
		transferSummary.addAll(processDataset(user_client , dataset_wpr))
	}
	return transferSummary
}


/**
 * Loop over images from a well
 * 
 * inputs
 * 		user_client : OMERO client
 * 		well_wpr_list : OMERO wells
 * 
 * */
def processWell(user_client, well_wpr_list){		
	List<Map<String, String>> transferSummary = new ArrayList<>()
	well_wpr_list.each{ well_wpr ->	
		IJLoggerInfo("OMERO","Working on well "+well_wpr.getName() +" : "+well_wpr.getId())
		well_wpr.getWellSamples().each{		
			transferSummary.addAll(processImage(user_client, it.getImage()))	
		}
	}	
	return transferSummary
}


/**
 * Loop over wells from a plate
 * 
 * inputs
 * 		user_client : OMERO client
 * 		plate_wpr_list : OMERO plates
 * 
 * */
def processPlate(user_client, plate_wpr_list){
	List<Map<String, String>> transferSummary = new ArrayList<>()
	plate_wpr_list.each{ plate_wpr ->	
		IJLoggerInfo("OMERO","Working on plate "+plate_wpr.getName() +" : "+plate_wpr.getId())
		transferSummary.addAll(processWell(user_client, plate_wpr.getWells(user_client)))
	} 
	return transferSummary
}


/**
 * Loop over plates from a screen
 * 
 * inputs
 * 		user_client : OMERO client
 * 		screen_wpr_List : OMERO screens
 * 
 * */
def processScreen(user_client, screen_wpr_list){
	List<Map<String, String>> transferSummary = new ArrayList<>()
	screen_wpr_list.each{ screen_wpr ->	
		IJLoggerInfo("OMERO","Working on screen "+screen_wpr.getName() +" : "+screen_wpr.getId())
		transferSummary.addAll(processPlate(user_client, screen_wpr.getPlates()))
	} 
	return transferSummary
}


/**
 * Create the CSV report from all info cleecting during the processing
 */
def generateCSVReport(transferSummaryList){
// define the header
	def headerList = [IMG_NAME, IMG_ID, CH_NAME]
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
	def name = getCurrentDateAndHour() + "_" + object_type + "_" + id + "_channel_names"
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
import fr.igred.omero.*
import ij.*
import java.nio.charset.StandardCharsets;
import javax.swing.JOptionPane;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;