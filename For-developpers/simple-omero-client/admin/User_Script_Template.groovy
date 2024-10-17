#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD


/* == CODE DESCRIPTION ==
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
 * == INPUTS ==
 *  - credentials  	
 * 
 * == OUTPUTS ==	
 *  - logs on IJ logger
 *  - Popup messages
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
 * date : 2023-11.07
 * version : v1.0
 * 
 * = HISTORY =
 * - 2023.11.07 : First release --v1.0
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