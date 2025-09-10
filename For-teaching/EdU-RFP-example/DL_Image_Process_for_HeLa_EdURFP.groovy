#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@String(label="Object to process", choices={"image","dataset","project","well","plate","screen"}) object_type
#@Long(label="Object ID", value=119273) id
#@String(label="Table name", value="ResultsTable") table_name
#@Boolean(label="Show images", value=false) showImages
#@Boolean(label="Delete existing ROIs", value=false) isDeleteExistingROIs
#@Boolean(label="Delete existing Tables", value=false) isDeleteExistingTables
#@Boolean(label="Send ROIs to OMERO", value=true) isSendNewROIs
#@Boolean(label="Send Measurements to OMERO", value=true) isSendNewMeasurements

#@ResultsTable rt_image
#@RoiManager rm


/* = CODE DESCRIPTION =
 * This is a template to interact with OMERO . 
 * User can specify the ID of an "image","dataset","project","well","plate","screen"
 * An image processing pipeline is applied on a dataset of images (HeLa cells). 
 * Results are then uploaded on OMERO
 * 
 * = BEFORE RUNNING =
 * - Check in "Analyze->Set Measurements..." ONLY the following checkboxes : Area, Mean gray value, Min & max gray value, Perimeter, Display label
 * 
 * = DATASET =
 *  - Downlaod HeLa cells images dataset from this zenodo repository : https://zenodo.org/record/4248921#.Ys0TM4RBybg
 *  - Import this dataset in OMERO. See the following link to know how to import data on OMERO : https://wiki-biop.epfl.ch/en/Image_Storage/OMERO/Importation
 * 
 * == INPUTS ==
 *  - credentials 
 *  - id 
 *  - object type
 *  - table name
 *  - choices to delete existing or import new ROIs/measurements
 * 
 * == OUTPUTS ==
 *  - open the image defined by id (or all images one after another from the dataset/project/... defined by id)
 *  - Send to OMERO computed ROIs/measurement if defined so.
 * 
 * = DEPENDENCIES =
 *  - Fiji update site OMERO 5.5-5.6
 *  - simple-omero-client 5.14.0 or later : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by romain guiet and Rémy Dornier, EPFL - SV - PTECH - BIOP 
 * version v2.0.2
 * 12.07.2022
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
 * - 2023-06-16 : Limits the number of call to the OMERO server + update the version of simple-omero-client to 5.12.3 + update documentation --v1.1
 * - 2023-06-29 : deletes tables with only one API call + move to simple-omero-client 5.14.0 --v1.2
 * - 2023-10-04 : Fix bug on counting the number of positive cells in each channel by adding new measurements --v1.3
 * - 2023-10-04 : Move from Li to Huang thresholding method --v1.3
 * - 2023-10-04 : Fix bug when deleting tables if there is not table to delete --v1.3
 * - 2023.11.14 : Update with user script template --v2.0
 * - 2024.05.10 : Update logger, CSV file generation and token separtor --v2.0.1
 * - 2025.09.10 : Save Fiji log window --v2.0.2
 */

/**
 * Main. 
 * Connect to OMERO, 
 * get the specified object (image, dataset, project, well, plate, screen), 
 * process all images contained in the specified object (segment cells and analyse detected cells in the 3 channels of each image), 
 * send ROIs of cells to OMERO
 * send the image table of measurements on OMERO and  
 * disconnect from OMERO
 * 
 */
 
 
IJ.run("Close All", "");
rm.reset()
rt_image.reset()

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
tokenSeparator = " | "
csvSeparator = ","

// global keys for the summary report
ROI_DEL = "Previous ROIs deleted"
TAB_DEL = "Previous tables deleted"
ROI_NEW = "New ROIs uploaded"
TAB_NEW = "New tables uploaded"
IPAS = "Image processed"
READ = "Imported on Fiji"
IMG_ID = "Image ID"
IMG_NAME = "Image name"

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
			message = "The images have all been processed !"

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


// add the Image Processing & Analysis part here 
def ipas(imp){
	def dapiCh_imp = new Duplicator().run(imp,1,1,1,1,1,1)
	def dapiCh_imp_final = dapiCh_imp.duplicate()
	// filtering
	IJ.run(dapiCh_imp, "Median...", "radius=3");

	// thresholding
	IJ.setAutoThreshold(dapiCh_imp, "Otsu dark");
	Prefs.blackBackground = true;
	IJ.run(dapiCh_imp, "Convert to Mask", "");
	
	// morphological improvements
	IJ.run(dapiCh_imp, "Close-", "");
	IJ.run(dapiCh_imp, "Fill Holes", "");

	// analyze connected components
	IJ.run("Set Measurements...", "area mean min centroid center perimeter display redirect=None decimal=3");
	IJ.run(dapiCh_imp, "Analyze Particles...", "clear add"); // If you clear, previous ROI are deleted

	// get Rois from Roi manager
	def rois = rm.getRoisAsArray()	
	
	// delete Roi manager
	rm.reset()
	
	def filtered_Rois = rois.findAll{ it.getStatistics().area > 50 }

	filtered_Rois.each{rm.addRoi(it)}
	int chN = imp.getNChannels()

	(1..chN).each{
		imp.setPosition(it,1,1)
		rm.runCommand(imp,"Measure");
	}
	
	rt_image = ResultsTable.getResultsTable("Results")
	rt_image.show("Image_Table")
}


/**
 * Manage OMERO-ImageJ connection for ROIs, image and tabes
 * 
 * inputs
 * 	 	user_client : OMERO client
 * 		img_wpr : OMERO image
 * 
 * */
def processImage(user_client, img_wpr){
	Map<String, String> imgSummaryMap = new HashMap<>()
	imgSummaryMap.put(IMG_NAME, img_wpr.getName())
	imgSummaryMap.put(IMG_ID, String.valueOf(img_wpr.getId()))
	
	// clear Fiji env
	IJ.run("Close All", "");
	rm.reset()
	rt_image.reset()

	IJLoggerInfo("OMERO","**** Working on image '" + img_wpr.getName() + "' ****") 
	ImagePlus imp
	try{
		IJLoggerInfo("OMERO / FIJI","Reading image on Fiji...") 
		imp = img_wpr.toImagePlus(user_client);
		imgSummaryMap.put(READ, "Done")
	}catch(Exception e){
		hasSilentlyFailed = true
		message = "The image cannot be read on Fiji"						
		IJLoggerError("OMERO / FIJI", message, e)
		imgSummaryMap.put(READ, "Failed")
		return imgSummaryMap
	}
	
	if (showImages) imp.show()
	
	// delete existing ROIs
	if(isDeleteExistingROIs){
		deleteROIs(user_client, img_wpr, imgSummaryMap)
	}
	
	// delete existing tables
	if(isDeleteExistingTables){			
		deleteTables(user_client, img_wpr, imgSummaryMap)
	}

	// do the processing here 
	IJLoggerInfo("FIJI","Run image processing : Start")
	try{
		ipas(imp)
		imgSummaryMap.put(IPAS, "Done")
	}catch(Exception e){
		hasSilentlyFailed = true
		message = "The processing of image '"+img_wpr.getName() +"' has failed"						
		IJLoggerError("FIJI", message, e)
		imgSummaryMap.put(IPAS, "Failed")
		return imgSummaryMap
	}
	IJLoggerInfo("FIJI","Run image processing : End")	
	
	// send ROIs to Omero
	if (isSendNewROIs){
		sendROIs(user_client, img_wpr, rm.getRoisAsArray() as List, imgSummaryMap)
	}
	
	// send table to OMERO
	if (isSendNewMeasurements){
		sendTables(user_client, img_wpr, rt_image, rm.getRoisAsArray() as List, imgSummaryMap)
	}	
	
	return imgSummaryMap
}



/**
 * delete existing ROIs on OMERO
 */
def deleteROIs(user_client, img_wpr, imgSummaryMap){
	try{
		IJLoggerInfo("OMERO","Deleting existing ROIs...") 
		def roisToDelete = img_wpr.getROIs(user_client)
		if (roisToDelete != null && !roisToDelete.isEmpty()){
			user_client.delete((Collection<GenericObjectWrapper<?>>)roisToDelete)
			IJLoggerInfo("OMERO","ROIs deleted !") 
			imgSummaryMap.put(ROI_DEL, "Done")
		}
		else{
			IJLoggerWarn("OMERO","No ROIs to delete")
		}
	}catch(Exception e){
		hasSilentlyFailed = true
		message = "An issue occurred when trying to delete ROIs"						
		IJLoggerError("OMERO", message, e)
		imgSummaryMap.put(ROI_DEL, "Failed")
	}
}


/**
 * Delete All existing tables on OMERO
 */
def deleteTables(user_client, img_wpr, imgSummaryMap){
	try{
		IJLoggerInfo("OMERO","Deleting existing tables...") 
		def tables_to_delete = img_wpr.getTables(user_client)
		if (tables_to_delete != null && !tables_to_delete.isEmpty()){
			user_client.deleteTables(tables_to_delete)
			IJLoggerInfo("OMERO","Tables deleted !") 
			imgSummaryMap.put(TAB_DEL, "Done")
		}
		else{
			IJLoggerWarn("OMERO","No tables to delete")
		}
	}catch(Exception e){
		hasSilentlyFailed = true
		message = "An issue occurred when trying to delete tables"						
		IJLoggerError("OMERO", message, e)
		imgSummaryMap.put(TAB_DEL, "Failed")
	}
}


/**
 * Send ImageJ ROIs to OMERO
 */
def sendROIs(user_client, img_wpr, fijiRoisToSend, imgSummaryMap){
	try{
		IJLoggerInfo("OMERO","Sending new ROIs...") 
		def roisToUpload = ROIWrapper.fromImageJ(fijiRoisToSend)
		img_wpr.saveROIs(user_client , roisToUpload)	
		imgSummaryMap.put(ROI_NEW, "Done")
	}catch(Exception e){
		hasSilentlyFailed = true
		message = "An issue occurred when trying to send ROIs"						
		IJLoggerError("OMERO", message, e)
		imgSummaryMap.put(ROI_NEW, "Failed")
	}
}


/**
 * Send Imagej resultsTable to OMERO
 */
def sendTables(user_client, img_wpr, rtToSend, fijiRois, imgSummaryMap){
	try{
		IJLoggerInfo("OMERO","Sending new table...") 
		TableWrapper table_wpr = new TableWrapper(user_client, rtToSend, img_wpr.getId(), fijiRois)
		table_wpr.setName(img_wpr.getName()+"_"+table_name)
		img_wpr.addTable(user_client, table_wpr)
		imgSummaryMap.put(TAB_NEW, "Done")
	}catch(Exception e){
		hasSilentlyFailed = true
		message = "An issue occurred when trying to send tables"						
		IJLoggerError("OMERO", message, e)
		imgSummaryMap.put(TAB_NEW, "Failed")
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
def processDataset(user_client, dataset_wpr){
	IJLoggerInfo("OMERO","Working on dataset '"+dataset_wpr.getName() +"' : "+dataset_wpr.getId())
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
def processProject(user_client, project_wpr){
	IJLoggerInfo("OMERO","Working on project '"+project_wpr.getName() +"' : "+project_wpr.getId())
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
		IJLoggerInfo("OMERO","Working on well '"+well_wpr.getName() +"' : "+well_wpr.getId())
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
		IJLoggerInfo("OMERO","Working on plate '"+plate_wpr.getName() +"' : "+plate_wpr.getId())
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
		IJLoggerInfo("OMERO","Working on screen '"+screen_wpr.getName() +"' : "+screen_wpr.getId())
		transferSummary.addAll(processPlate(user_client, screen_wpr.getPlates()))
	} 
	return transferSummary
}


/**
 * Create the CSV report from all info cleecting during the processing
 */
def generateCSVReport(transferSummaryList){
	// define the header
	def headerList = [IMG_NAME, IMG_ID, READ, IPAS, ROI_DEL, TAB_DEL, ROI_NEW, TAB_NEW]
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
	def name = getCurrentDateAndHour() + "_List_of_processed_images"
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
import fr.igred.omero.roi.*
import fr.igred.omero.repository.*
import fr.igred.omero.annotations.*
import ij.*
import ij.plugin.*
import ij.measure.ResultsTable
import java.nio.charset.StandardCharsets;
import javax.swing.JOptionPane;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;