#@String(label="Host", value="omero-server.epfl.ch", persist=true) host
#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD


/* Code description
 *  
 * This script is a template to create user scripts, with popup messages and csv reports, as well as IJ log messages for
 * traceback.
 * 
 * Basically, the user script should contains
 * 	- 	As many error handling as possible with try-catch statements. Some errors should stop the script 
 * 		(with the variable 'hasfailed' turned to 'true') and some other errors may be catched without stopping the script,
 * 		in a loop, for example (with the variable 'hasSilentlyfailed' turned to 'true'). In your code, you shouldn't turn both
 * 		variables to 'true' (i.e. mutually exculsive).
 * 		
 * 	-	Popup messages : There is no need to add new popup messages unless you think it is necessary. Keep the try-catch
 * 		structure as described above, and populate the golbal variable 'message'. Popup are displayed only at the end of 
 * 		the script, with the right message, according to what has been failed or not.
 * 		
 * 	-	CSV report : The report has a standardized format (CSV), with a header. The column headers are given by the global
 * 		keys. These keys are also used to create a map for each entry (ie. one entry can be an image, a file, a tag...) 
 * 		populated with the corresponding values. Each map is stored in the list 'transferSummary', which is the only
 * 		parameter of the function 'generateCSVReport'. Thie method build the CSV format based on the maps. For each key,
 * 		you should check if the key exists or not. If it doesn't exist, it may mean that something has failed during
 * 		the execution and the value should be turned to 'failed' or ' - '. Finally, do not forget to add a meaningful name
 * 		to teh report. The current date (yyyymmjj-hhmmss) is automatically added as a prefix to the name. The report is
 * 		then saved in the Downloads folder.
 * 		
 * 	You can have a look to existing user scripts to have a concrete example.
 * 	
 * 
 * Dependencies
 *  - Fiji update site OMERO 5.5-5.6
 *  - Fiji update site PTBIOP, with simple-omero-client
 * 
 * Author: Rémy Dornier, EPFL - PTBIOP 
 * Date: 2023.11.07
 * Version: 1.0.0
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
 */


// global variables for popup messages
hasFailed = false
hasSilentlyFailed = false
message = ""
tokenSeparator = " | "
csvSeparator = ","

// global keys for the summary report
KEY1= "a"
KEY2 = "b"
KEY3 = "c"
KEY4 = "d"


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
		//
		// do some stuff
		//
		
		
		try{
			//
			// do some stuff. If there is an error here, the script stops and jump to the general catch
			//
		}catch(Exception e){
			hasFailed = true
			message = "Error message saying what goes wrong"
			IJLoggerError("OMERO", message)
			throw e
		}
		
		//
		// do some stuff.
		//
		
		for(int i = 0; i < 2; i++){
			
			Map<String, String> imgSummaryMap = new HashMap<>()
			imgSummaryMap.put(KEY1, "Common value that must appear in all summaryMaps - 1")
			imgSummaryMap.put(KEY2, "Common value that must appear in all summaryMaps - 2")
		
			//
			// do some stuff.
			//
			
			try{
				//
				// do some stuff. If there is an error here, the script just continue and does not crash
				// but this failure should appear in the summary report
				//
				
				imgSummaryMap.put(KEY3, "Only if the above has been executed")
			}catch(Exception e){
				hasSilentlyFailed = true
				message = "Error message saying what's wrong"						
				IJLoggerError("OMERO", message, e)
				
				imgSummaryMap.put(KEY4, "Only if there is an issue")
				
				continue
			}
			
			//
			// do some stuff.
			//
			
			transferSummary.add(imgSummaryMap)
		}
		
		
		//
		// do some stuff.
		//
		
		if(hasSilentlyFailed)
			message = "The script ended with some errors."
		else 
			message = "The tags have been successfully replaced."
			
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
					JOptionPane.showMessageDialog(null, message, "WARNING", JOptionPane.WARNING_MESSAGE);
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
 * Create the CSV report from all info cleecting during the processing
 */
def generateCSVReport(transferSummaryList){
// define the header
	def headerList = [KEY1, KEY2, KEY3, KEY4]
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
	def name = getCurrentDateAndHour() + "_My_report_name"
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
import fr.igred.omero.repository.*
import fr.igred.omero.annotations.*
import java.nio.charset.StandardCharsets;
import ij.IJ;
import javax.swing.JOptionPane;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;