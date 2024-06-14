#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@Boolean(label="Delete unlinked files", value=false, persist=false) toDelete
#@String(choices={"Only my files", "All files within my group"}, style="radioButtonHorizontal", persist=false) choice


/*  This script aims at deleting all orphaned attachments from your OMERO account / group.
 *  An orphaned attachment is an attachment that is NOT linked to ANY image/container i.e. not used anymore on OMERO
 *  If your are a group owner, you'll have the choice to delete all orphaned files from your group 
 * == INPUTS ==
 *  - credentials 
 *  - Your choice about orphaned files deletion
 *  - Your choice about where to search files (in your group or just for you)
 *    By default, it will only search all files you own
 * 
 * == OUTPUTS ==
 *  - orphaned files deletion on OMERO
 *  - CSV report with the list of deleted files and remaining files
 * 
 * = DEPENDENCIES =
 *  - Fiji update site OMERO 5.5-5.6
 *  - simple-omero-client-5.15.0 or later : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by Rémy Dornier, EPFL - SV - PTECH - BIOP 
 * 27.02.2023
 * version v2.0.1
 * 
 * = COPYRIGHT =
 * © All rights reserved. ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP), 2024
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
 * - 2023.11.07 : Update script with formatted CSV file, popup messages and error catching --v2.0
 * - 2024.05.10 : Update logger, CSV file generation and token separtor --v2.0.1
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

hasFailed = false
hasSilentlyFailed = false
message = ""
tokenSeparator = " | "
csvSeparator = ","

FL_NAME = "File name"
FL_ID = "File ID"
FL_SIZE = "File size (bytes)"
FL_FORMAT = "File format"
OWN = "Owner"
GRP = "Group"
STS = "Deleted"
PRT = "Parent_Name_Id"
ORPH = "Unlinked file"

if (user_client.isConnected()){
	IJLoggerInfo("OMERO","Connected to "+host)
	List<Map<String, String>> transferSummary = new ArrayList<>()
	
	try{
		// find all files on current omero group
		def fileWrapperList = []
		Map<long, ExperimenterData> experimenters = new HashMap<>()
		def groupData
		def experimenterData
		
		// get user account / group
		try{
			groupData = user_client.getGateway().getAdminService(user_client.getCtx()).getGroup(user_client.getCurrentGroupId())
			experimenterData = user_client.getGateway().getAdminService(user_client.getCtx()).getExperimenter(user_client.getUser().getId())
			experimenters.put(user_client.getUser().getId(), experimenterData)
		}catch(Exception e){
		    hasFailed = true
			message = "An error occurred when retrieving your account / group account"
			IJLoggerError("OMERO", message)
			throw e
		}
		
		// retrieve group attachments
		if(choice.equals("All files within my group")){
			IJLoggerInfo("OMERO", "Get all files from your group")
			try{
				fileWrapperList = getGroupAttachments(user_client)
			}catch(Exception e){
			    hasFailed = true
				message = "Cannot retrieve all attachments from your group"
				IJLoggerError("OMERO", message)
				throw e
			}
		}
		// retrieve user attachments
		else{
			IJLoggerInfo("OMERO", "Get all files from your account")
			try{
				fileWrapperList = getExperimenterAttachments(user_client)
			}catch(Exception e){
			    hasFailed = true
				message = "Cannot retrieve all attachments from your account"
				IJLoggerError("OMERO", message)
				throw e
			}	
		}
			
		List<FileAnnotationWrapper> orphanedFiles = []
		List<FileAnnotationWrapper> linkedFiles = []
		Map<long, List<IObject>> linkedParentsMap = new HashMap<>()

		// filter orphaned and linked files
		try{
			IJLoggerInfo("OMERO", "Filter all orphaned files and get links from non-orphaned files...")
			(orphanedFiles, linkedFiles, linkedParentsMap) = filterOrphanedAttachments(user_client, fileWrapperList)
		}catch(Exception e){
		    hasFailed = true
			message = "The links between files and images/containers cannot be retrieved"
			IJLoggerError("OMERO", message)
			throw e
		}
		
		// sort both list by file name
		orphanedFiles.sort(Comparator.comparing(FileAnnotationWrapper::getFileName));
		linkedFiles.sort(Comparator.comparing(FileAnnotationWrapper::getFileName));
						
		// summary for orphaned files
		orphanedFiles.each{file->
			Map<String, String> fileSummaryMap = summarizeFileInfo(user_client, file, experimenters, groupData)
			fileSummaryMap.put(ORPH, "Yes")
			
			// delete orphaned files
			if(toDelete){
				try{
					IJLoggerInfo("OMERO", "Delete orphaned file '" + file.getFileName() + "'")
					user_client.delete(file.asIObject())
					fileSummaryMap.put(STS, "Yes")
				}catch(Exception e){
				    hasSilentlyFailed = true
				    fileSummaryMap.put(STS, "No")
					message = "Cannot delete orphaned files"
					IJLoggerError("OMERO", message)
					IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e))
				}
			}
			transferSummary.add(fileSummaryMap)
		}
		
		// summarizes the linked files info
		linkedFiles.each{file->
			Map<String, String> fileSummaryMap = summarizeFileInfo(user_client, file, experimenters, groupData)
			fileSummaryMap.put(ORPH, "No")

			// add parents to the csv file
			linkedParents = linkedParentsMap.get(file.getId())
			def formattedParentList = []
			linkedParents.each{link -> 
				if(link instanceof omero.model.ExperimenterI)
					formattedParentList.add(link.class.toString().replace("class omero.model.","") + "_" + link.getOmeName().getValue() + "_" + link.getId().getValue())
				else
					formattedParentList.add(link.class.toString().replace("class omero.model.","") + "_" + link.getName().getValue() + "_" + link.getId().getValue())
			}
			
			fileSummaryMap.put(PRT, formattedParentList.join(tokenSeparator))
			transferSummary.add(fileSummaryMap)
		}
		
		// final message
		if(hasSilentlyFailed){
			message = "The script ended with some errors."
		}
		else {
			if(!toDelete || orphanedFiles.isEmpty()){
				message = "No orphaned files to delete."
				IJLoggerWarn("OMERO", message)
			}
			else
				message = "The orphaned files have been successfully deleted."
		}
		
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
 * retrieve all attachments within the default group
 */
def getGroupAttachments(user_client){
	return user_client.findByQuery("select an from FileAnnotation as an join fetch an.file where an.ns is not Null")
								.stream()
								.map(FileAnnotationData::new)
								.map(FileAnnotationWrapper::new)
								.collect(Collectors.toList())
}


/**
 * retrieve all attachments owner by the current logged in user
 */
def getExperimenterAttachments(user_client){
	return user_client.findByQuery("select an from FileAnnotation as an left outer join fetch an.file" +
														" left outer join fetch an.details.owner as o " +
												         "where o.id = "+ user_client.getUser().getId() + " and an.ns is not Null")
								.stream()
								.map(FileAnnotationData::new)
								.map(FileAnnotationWrapper::new)
								.collect(Collectors.toList())
}


/**
 * sort orphaned from non orphaned attachments and get all the link to images / container for non orphaned files
 */
def filterOrphanedAttachments(user_client, fileWrapperList){			
	List<FileAnnotationWrapper> orphanedFilesWpr = new ArrayList<>()
	List<FileAnnotationWrapper> linkedFilesWpr = new ArrayList<>()
	Map<long, List<IObject>> linkedParentsMap = new HashMap<>()
	
	fileWrapperList.each{
		def listOfParents = user_client.findByQuery("select link.parent from ome.model.IAnnotationLink link " +
					"where link.child.id=" +it.getId())
		
		// orphaned files
		if(listOfParents.size() == 0)
			orphanedFilesWpr.add(it)
		else{
			// linked files
			linkedFilesWpr.add(it)
			linkedParentsMap.put(it.getId(), listOfParents)
		}
	}
	
	return [orphanedFilesWpr, linkedFilesWpr, linkedParentsMap]
}


/**
 * create a summary map of the attachment
 */
def summarizeFileInfo(user_client, file, experimenters, groupData){
	Map<String, String> fileSummaryMap = new HashMap<>()
	fileSummaryMap.put(FL_NAME, file.getFileName())
	fileSummaryMap.put(FL_ID, file.getFileID())
	fileSummaryMap.put(FL_FORMAT, file.getFileFormat())
	fileSummaryMap.put(FL_SIZE, file.getFileSize())
	fileSummaryMap.put(GRP, groupData.getName().getValue())
	
	if(!experimenters.containsKey(file.getOwner().getId())){
		def experimenter = user_client.getGateway().getAdminService(user_client.getCtx()).getExperimenter(file.getOwner().getId())
		experimenters.put(file.getOwner().getId(), experimenter)
		fileSummaryMap.put(OWN, experimenter.getOmeName().getValue())
	}
	else
		fileSummaryMap.put(OWN, experimenters.get(file.getOwner().getId()).getOmeName().getValue())
	
	return fileSummaryMap
}


/**
 * Create teh CSV report from all info cleecting during the processing
 */
def generateCSVReport(transferSummaryList){
	def headerList = [STS, ORPH, PRT, FL_NAME, FL_ID, FL_FORMAT, FL_SIZE, OWN, GRP]
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
	def name = getCurrentDateAndHour()+"_Delete_orphaned_file"
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
def IJLoggerError(String message){
	IJ.log(getCurrentDateAndHour() + "   [ERROR]        "+message); 
}
def IJLoggerError(String title, String message){
	IJ.log(getCurrentDateAndHour() + "   [ERROR]        ["+title+"] -- "+message); 
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
import omero.gateway.model.FileAnnotationData
import omero.gateway.model.ExperimenterData
import omero.model.IObject
import java.util.stream.Collectors;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import javax.swing.JOptionPane; 
import ij.IJ;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;