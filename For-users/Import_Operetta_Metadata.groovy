#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@String(label="Object to process", choices={"plate","screen"}) object_type
#@Long(label="Object ID", value=119273) id
#@File(label="CSV file of plate info", value="") csvFile

/* = CODE DESCRIPTION =
 * - The user enter the plate ID and give the path to his/her csv file containing the plate layout information foamtted as described here:
 * https://wiki-biop.epfl.ch/en/Image_Storage/OMERO/Importation
 * - The code reads the csv file and extracts key-values.
 * - Each of the key-value is then imported on corresponding images on OMERO
 * 
 * == INPUTS ==
 *  - credentials 
 *  - id
 *  - object type
 *  - CSV file to read
 * 
 * == OUTPUTS ==
 *  - key value on OMERO
 *  - CSV report in the Downloads folder
 * 
 * = DEPENDENCIES =
 *  - Fiji update site OMERO 5.5-5.6
 *  - simple-omero-client-5.9.2 or later : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by Rémy Dornier, EPFL - SV - PTECH - BIOP 
 * 22.08.2022
 * version 2.0
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
 * == Bug Fix ==
 * - 2022.10.05 : make explicit .equals and convert String to Integer
 * - 2022.11.02 : can now select a screen and process each plate inside
 * - 2023.06.19 : Remove unnecessary imports
 * - 2023.11.08 : Add popup messages, IJ logs and CSV report --v2.0
 */

/**
 * Main. 
 */

// Connection to server
host = "omero-poc.epfl.ch"
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
WELL_NAME = "Well name"
PLT_NAME = "Plate name"
SCR_NAME = "Screen name"
KVP = "Key values"
STS = "Status"

if (user_client.isConnected()){
	IJLoggerInfo("OMERO","Connected to "+host)
	List<Map<String, String>> transferSummary = new ArrayList<>()
	
	try{
		switch (object_type){
			case "plate":
				transferSummary = processPlate(user_client, user_client.getPlates(id), null)
				break
			case "screen":
				transferSummary = processScreen(user_client, user_client.getScreens(id))
				break
		}
				
		if(hasSilentlyFailed)
			message = "The script ended with some errors."
		else 
			message = "The KVPs have been successfully added."
			
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
 * Add the key value to OMERO attach to the current repository wrapper
 * 
 * inputs
 * 		user_client : OMERO client
 * 		repository_wpr : OMERO repository object (image, dataset, project, well, plate, screen)
 * 
 * */
def addKeyValuesToOMERO(user_client, repository_wpr, keyValues){
	MapAnnotationWrapper newKeyValues = new MapAnnotationWrapper(keyValues)
	newKeyValues.setNameSpace("openmicroscopy.org/omero/client/mapAnnotation")
	repository_wpr.addMapAnnotation(user_client, newKeyValues)
}



/**
 * Read the given csv file. The file should be formatted as below : 
 * 	- First column : Row of the well
 * 	- Second column : Column of the well
 * 	- other columns : key-values (key on the column header and values below)
 * 	=> one row = one well + header at the begining formatted like "Row,Column,Key1,Key2,Key3,..."
 * 
 * inputs
 * 		user_client : OMERO client
 * 		well_wpr_list : OMERO wells
 * 
 * */
def processWell(user_client, well_wpr_list, screen_wpr, plate_wpr){	
	// read the csv file
	def lines
	try{
		lines = csvFile.readLines()
	}catch(Exception e){
		hasFailed = true
		message = "The CSV file you provided cannot be read : "+csvFile.getAbsolutePath()
		IJLoggerError("OMERO", message)
		throw e
	}
	List<Map<String, String>> transferSummary = new ArrayList<>()
	def header = lines[0].split(",")
	
	well_wpr_list.each{ well_wpr ->			
		def wellLine = ""
		
		// find the current well in the csv file
		for(int i = 1; i<lines.size(); i++){
  			wellLine = lines[i].split(",")
  			if(wellLine[0].equals(well_wpr.identifier(well_wpr.getRow().intValue() + 1)) && Integer.parseInt(wellLine[1]).equals(well_wpr.getColumn().intValue() + 1)){ // see 05.10.2022 fix
  				break
  			}
		}
		
		// create the key-values for the current well
		List<NamedValue> keyValues = new ArrayList()
		for(int i = 0; i<wellLine.size(); i++){
			keyValues.add(new NamedValue(header[i], wellLine[i])) 
		}

		// add key-values to images within the current well on OMERO
		addKeyValuesToOMERO(user_client, well_wpr, keyValues)
		well_wpr.getWellSamples().each{	
			def imageWpr = it.getImage()
			Map<String, String> imgSummaryMap = new HashMap<>()
			imgSummaryMap.put(IMG_NAME, imageWpr.getName().replace(",",";"))
			imgSummaryMap.put(IMG_ID, imageWpr.getId())
			imgSummaryMap.put(WELL_NAME, well_wpr.getName().replace(",",";"))
			imgSummaryMap.put(PLT_NAME, plate_wpr.getName().replace(",",";"))
			imgSummaryMap.put(SCR_NAME, screen_wpr == null ? " - " : screen_wpr.getName().replace(",",";"))
			
			try{
				IJLoggerInfo("OMERO", "Adding key-values on image '"+imageWpr.getName()+"'")
				addKeyValuesToOMERO(user_client, imageWpr, keyValues)	
				imgSummaryMap.put(KVP, keyValues.collect{it.name + ":"+it.value}.join(" ; "))
				imgSummaryMap.put(STS, "Added")
			}catch(Exception e){
				hasSilentlyFailed = true
				message = "Cannot add KVPs on image '"+imageWpr.getName()+"'"					
				IJLoggerError("OMERO", message)
				IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e))
			}
			
			transferSummary.add(imgSummaryMap)
		}
	}	
	
	return transferSummary
}


/**
 * get all wells within plates
 * 
 * inputs
 * 		user_client : OMERO client
 * 		plate_wpr_list : OMERO list of plates
 * 
 * */
def processPlate(user_client, plate_wpr_list, screen_wpr){
	List<Map<String, String>> transferSummary = []
	plate_wpr_list.each{ plate_wpr ->	
		transferSummary.addAll(processWell(user_client, plate_wpr.getWells(user_client), screen_wpr, plate_wpr))
	} 
	return transferSummary
}



/**
 * get all plates within screens
 * 
 * inputs
 * 	 	user_client : OMERO client
 * 		screen_wpr_list : OMERO list of screens
 * 
 * */
def processScreen(user_client, screen_wpr_list){
	List<Map<String, String>> transferSummary = []
	screen_wpr_list.each{ screen_wpr ->	
		transferSummary.addAll(processPlate(user_client, screen_wpr.getPlates(), screen_wpr))
	} 
	return transferSummary
}


/**
 * Create the CSV report from all info cleecting during the processing
 */
def generateCSVReport(transferSummaryList){
	// define the header
	String header = IMG_NAME + "," + IMG_ID + "," + WELL_NAME + "," + PLT_NAME + "," + SCR_NAME + "," + KVP + "," + STS

	String statusOverallSummary = ""

	transferSummaryList.each{imgSummaryMap -> 
		String statusSummary = ""
		
		// For keys that should always exist
		statusSummary += imgSummaryMap.get(IMG_NAME)+","
		statusSummary += imgSummaryMap.get(IMG_ID)+","
		statusSummary += imgSummaryMap.get(WELL_NAME)+","
		statusSummary += imgSummaryMap.get(PLT_NAME)+","
		statusSummary += imgSummaryMap.get(SCR_NAME)+","
		
		// in case of error, the results for that key is failed
		if(imgSummaryMap.containsKey(KVP))
			statusSummary += imgSummaryMap.get(KVP)+","
		else
			statusSummary +=  " - ,"

		// Nothing to add if there is no error
		if(imgSummaryMap.containsKey(STS))
			statusSummary += imgSummaryMap.get(STS)+","
		else
			statusSummary += "Failed"
		
		statusOverallSummary += statusSummary + "\n"
	}
	String content = header + "\n"+statusOverallSummary
					
	// save the report
	def name = getCurrentDateAndHour() + "_Key_values_to_" + object_type + "_" + id
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
import fr.igred.omero.*
import fr.igred.omero.repository.*
import fr.igred.omero.annotations.*
import omero.gateway.model.*
import omero.model.NamedValue
import java.io.File
import java.lang.Integer
import java.nio.charset.StandardCharsets;
import ij.IJ;
import javax.swing.JOptionPane;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;