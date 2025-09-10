#@String(label="Host", value="omero-server.epfl.ch", persist=true) host
#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@String(label="Option", choices={"Report only", "Report and delete"}, style="radioButtonHorizontal", value="Report only", persist=false) dryRun
#@String(label="WHICH ORPHANED ANNOTATION TO DELETE", visibility=MESSAGE, required=false) msg1
#@Boolean(label="Files", value=false, persist=false) filesToDelete
#@Boolean(label="Tags", value=false, persist=false) tagsToDelete
#@Boolean(label="Key-Values", value=false, persist=false) kvpsToDelete
#@Boolean(label="Comments", value=false, persist=false) commentsToDelete
#@Boolean(label="Ratings", value=false, persist=false) ratingsToDelete


/*  This script aims at deleting all orphaned annotations from your OMERO account / group.
 *  An orphaned annotation is an object that is NOT ACCESSIBLE anymore from the OMERO webclient
 *  If your are a group owner, you'll have the choice to delete all orphaned files from your group 
 *  
 * == INPUTS ==
 *  - credentials 
 *  - processing choice ; by default, the "report only" option is selected => it will only generate a csv file with all annotations that will be deleted 
 *  but doesn't delete anything. The "report and delete" option will effectively deleted all orphaned annotations.
 *  - annotations you want to delete
 * 
 * == OUTPUTS ==
 *  - CSV report with the list of deleted files and remaining files
 * 
 * = DEPENDENCIES =
 *  - Fiji update site OMERO 5.5-5.6
 *  - simple-omero-client-5.18.0 or later : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by Rémy Dornier, EPFL - SV - PTECH - BIOP 
 * 2024.06.14
 * version v1.0.1
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
 * - 2024.06.14: first release --v1.0.0
 * - 2025.09.10: Save Fiji log window --v1.0.1
 * - 2025.09.01: Adding host in UI --v1.0.1
 */


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

hasFailed = false
hasSilentlyFailed = false
message = ""
tokenSeparator = " | "
csvSeparator = ","

NAME = "Name"
ID = "ID"
FL_SIZE = "File size (bytes)"
FL_FORMAT = "File format"
OWN = "Owner"
GRP = "Group"
STS = "Status"
TYPE = "Type"

if (user_client.isConnected()){
	IJLoggerInfo("OMERO","Connected to "+host)
	List<Map<String, String>> transferSummary = new ArrayList<>()
	
	try{
		// find all files on current omero group
		def orphanedAnnotationWrapperList = new ArrayList<>()

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
		
		// retrieve group annotations
		if(filesToDelete){
			try{
				IJLoggerInfo("OMERO", "Get all orphaned files from your group")
				orphanedAnnotationWrapperList.addAll(getOrphanedFileAnnotations(user_client))
			}catch(Exception e){
				hasSilentlyFailed = true
				message = "Cannot retrieve orphaned files from your group"
				IJLoggerError("OMERO", message, e)
			}
		}
		if(tagsToDelete){
			try{
				IJLoggerInfo("OMERO", "Get all orphaned tags from your group")
				orphanedAnnotationWrapperList.addAll(getOrphanedTagAnnotations(user_client))
			}catch(Exception e){
				hasSilentlyFailed = true
				message = "Cannot retrieve orphaned tags from your group"
				IJLoggerError("OMERO", message, e)
			}
		}
		if(kvpsToDelete){
			try{
				IJLoggerInfo("OMERO", "Get all orphaned key-values from your group")
				orphanedAnnotationWrapperList.addAll(getOrphanedMapAnnotations(user_client))
			}catch(Exception e){
				hasSilentlyFailed = true
				message = "Cannot retrieve orphaned key-values from your group"
				IJLoggerError("OMERO", message, e)
			}
		}
		if(commentsToDelete){
			try{
				IJLoggerInfo("OMERO", "Get all orphaned comments from your group")
				orphanedAnnotationWrapperList.addAll(getOrphanedCommentAnnotations(user_client))
			}catch(Exception e){
				hasSilentlyFailed = true
				message = "Cannot retrieve orphaned comments from your group"
				IJLoggerError("OMERO", message, e)
			}
		}
		if(ratingsToDelete){
			try{
				IJLoggerInfo("OMERO", "Get all orphaned ratings from your group")
				orphanedAnnotationWrapperList.addAll(getOrphanedRatingAnnotations(user_client))
			}catch(Exception e){
				hasSilentlyFailed = true
				message = "Cannot retrieve orphaned ratings from your group"
				IJLoggerError("OMERO", message, e)
			}
		}
					
						
		// summary for orphaned files
		orphanedAnnotationWrapperList.each{ann->
			Map<String, String> fileSummaryMap = summarizeInfo(user_client, ann, experimenters, groupData)
			fileSummaryMap.put(TYPE, ann.getClass().getSimpleName())
			
			if(dryRun.equals("Report only"))
				fileSummaryMap.put(STS, "Dry Run")
			
			transferSummary.add(fileSummaryMap)
		}
		
		// delete annotations
		if(dryRun.equals("Report and delete") && !orphanedAnnotationWrapperList.isEmpty()){
			def status = deleteAnnotations(user_client, orphanedAnnotationWrapperList, transferSummary)
			transferSummary.each{a->a.put(STS, status)}
		}
		
		// final message
		if(hasSilentlyFailed){
			message = "The script ended with some errors."
		}
		else {
			if(orphanedAnnotationWrapperList.isEmpty()){
				message = "No orphaned annotations to delete."
				IJLoggerWarn("OMERO", message)
			}
			else{
				if(dryRun.equals("Report and delete"))
					message = "The orphaned annotations have been successfully deleted."
				else
					message = "The orphaned annotations have been successfully reported."
			}
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
 * delete all annotations at once (one server call)
 */
def deleteAnnotations(user_client, annList, transferSummary){
	def status = ""
	try{
		IJLoggerInfo("OMERO", "Delete orphaned annotations...")
		def IObjectList = annList.collect{e-> e.asIObject()}
		user_client.delete(IObjectList)
		status = "Deleted"
	}catch(Exception e){
	    hasSilentlyFailed = true
	    status = "Failed"
		message = "Cannot delete orphaned annotations"
		IJLoggerError("OMERO", message, e)
	}
	
	return status
}

/**
 * retrieve all attachments owner by the current logged in user
 */
def getOrphanedTagAnnotations(user_client){
	return user_client.findByQuery("select a from TagAnnotation a " + getCommonQueryPart())
								.stream()
								.map(TagAnnotationData::new)
								.map(TagAnnotationWrapper::new)
								.collect(Collectors.toList())
}

/**
 * retrieve all attachments owner by the current logged in user
 */
def getOrphanedMapAnnotations(user_client){
	return user_client.findByQuery("select a from MapAnnotation a " + getCommonQueryPart())
								.stream()
								.map(MapAnnotationData::new)
								.map(MapAnnotationWrapper::new)
								.collect(Collectors.toList())
}

/**
 * retrieve all attachments owner by the current logged in user
 */
def getOrphanedCommentAnnotations(user_client){
	return user_client.findByQuery("select a from CommentAnnotation a " + getCommonQueryPart())
								.stream()
								.map(TextualAnnotationData::new)
								.map(TextualAnnotationWrapper::new)
								.collect(Collectors.toList())
}

/**
 * retrieve all attachments owner by the current logged in user
 */
def getOrphanedRatingAnnotations(user_client){
	return user_client.findByQuery("select a from LongAnnotation a " + getCommonQueryPart())
								.stream()
								.map(RatingAnnotationData::new)
								.map(RatingAnnotationWrapper::new)
								.collect(Collectors.toList())
}

/**
 * retrieve all attachments owner by the current logged in user
 */
def getOrphanedFileAnnotations(user_client){
	return user_client.findByQuery("select a from FileAnnotation a join fetch a.file " + getCommonQueryPart())
								.stream()
								.map(FileAnnotationData::new)
								.map(FileAnnotationWrapper::new)
								.collect(Collectors.toList())
}



def getCommonQueryPart(){
	return "where a.ns is Null "+
			"and a.id not in (select l.child.id from ProjectAnnotationLink l) "+
		    "and a.id not in (select l.child.id from DatasetAnnotationLink l) "+
		    "and a.id not in (select l.child.id from ImageAnnotationLink l) "+
		    "and a.id not in (select l.child.id from ScreenAnnotationLink l) "+
		    "and a.id not in (select l.child.id from PlateAnnotationLink l) "+
		    "and a.id not in (select l.child.id from PlateAcquisitionAnnotationLink l) "+
		    "and a.id not in (select l.child.id from WellAnnotationLink l)"
}

/**
 * retrieve all attachments owner by the current logged in user
 * https://forum.image.sc/t/searching-by-roi-comment-field-in-omero-web/38808/2
 */
/*def getOrphanedRoiAnnotations(user_client){
	return user_client.findByQuery("select a from Roi as a join fetch a.shapesSeq as s where a.image is Null")//("select roiLink from RoiAnnotationLink as roiLink left outer join fetch roiLink.parent as roi")//+" where roiLink.parent.image is not NULL")
								.stream()
								//.map(RoiAnnotationLink::new)
								//.map(RoiAnnotationLink::getParent)
								//.map(ROIData::new)
								//.map(ROIWrapper::new)
								.collect(Collectors.toList())
}*/


def summarizeInfo(user_client, ann, experimenters, groupData){
	def className = ann.getClass().getSimpleName()
	if(className.equals("TagAnnotationWrapper"))
		return summarizeTagAnnotationInfo(user_client, ann, experimenters, groupData)
	else if(className.equals("MapAnnotationWrapper"))
		return summarizeMapAnnotationInfo(user_client, ann, experimenters, groupData)
	else if(className.equals("FileAnnotationWrapper"))
		return summarizeFileAnnotationInfo(user_client, ann, experimenters, groupData)
	else if(className.equals("RatingAnnotationWrapper"))
		return summarizeRatingAnnotationInfo(user_client, ann, experimenters, groupData)
	else if(className.equals("TextualAnnotationWrapper"))
		return summarizeCommentAnnotationInfo(user_client, ann, experimenters, groupData)
}

/**
 * create a summary map of the attachment
 */
def summarizeFileAnnotationInfo(user_client, file, experimenters, groupData){
	Map<String, String> fileSummaryMap = new HashMap<>()
	fileSummaryMap.put(NAME, file.getFileName())
	fileSummaryMap.put(ID, file.getFileID())
	fileSummaryMap.put(FL_FORMAT, file.getFileFormat())
	fileSummaryMap.put(FL_SIZE, file.getFileSize())
	fileSummaryMap.putAll(summarizeOwnerInfo(user_client, file, experimenters, groupData))
	
	return fileSummaryMap
}


/**
 * create a summary map of the tags
 */
def summarizeTagAnnotationInfo(user_client, tag, experimenters, groupData){
	Map<String, String> tagSummaryMap = new HashMap<>()
	tagSummaryMap.put(NAME, tag.getName())
	tagSummaryMap.put(ID, tag.getId())
	tagSummaryMap.putAll(summarizeOwnerInfo(user_client, tag, experimenters, groupData))
	
	return tagSummaryMap
}

/**
 * create a summary map of the key-values
 */
def summarizeMapAnnotationInfo(user_client, kvp, experimenters, groupData){
	Map<String, String> mapSummaryMap = new HashMap<>()
	mapSummaryMap.put(NAME, kvp.getContent().collect{e->e.name +"="+e.value}.join(tokenSeparator))
	mapSummaryMap.put(ID, kvp.getId())
	mapSummaryMap.putAll(summarizeOwnerInfo(user_client, kvp, experimenters, groupData))
	
	return mapSummaryMap
}

/**
 * create a summary map of the rating
 */
def summarizeRatingAnnotationInfo(user_client, rating, experimenters, groupData){
	Map<String, String> ratingSummaryMap = new HashMap<>()
	ratingSummaryMap.put(NAME, rating.getRating())
	ratingSummaryMap.put(ID, rating.getId())
	ratingSummaryMap.putAll(summarizeOwnerInfo(user_client, rating, experimenters, groupData))
	
	return ratingSummaryMap
}

/**
 * create a summary map of the comment
 */
def summarizeCommentAnnotationInfo(user_client, comment, experimenters, groupData){
	Map<String, String> commentSummaryMap = new HashMap<>()
	commentSummaryMap.put(NAME, comment.getText())
	commentSummaryMap.put(ID, comment.getId())
	commentSummaryMap.putAll(summarizeOwnerInfo(user_client, comment, experimenters, groupData))
	
	return commentSummaryMap
}

/**
 * create a summary map of the owner
 */
def summarizeOwnerInfo(user_client, ann, experimenters, groupData){
	Map<String, String> summaryMap = new HashMap<>()
	summaryMap.put(GRP, groupData.getName().getValue())
	
	if(!experimenters.containsKey(ann.getOwner().getId())){
		def experimenter = user_client.getGateway().getAdminService(user_client.getCtx()).getExperimenter(ann.getOwner().getId())
		experimenters.put(ann.getOwner().getId(), experimenter)
		summaryMap.put(OWN, experimenter.getOmeName().getValue())
	}
	else
		summaryMap.put(OWN, experimenters.get(ann.getOwner().getId()).getOmeName().getValue())
	
	return summaryMap
}


/**
 * Create the CSV report from all info collecting during the processing
 */
def generateCSVReport(transferSummaryList){
	def headerList = [TYPE, NAME, ID, FL_FORMAT, FL_SIZE, OWN, GRP, STS]
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
	def name = getCurrentDateAndHour()+"_Delete_orphaned_annotations"
	String path = System.getProperty("user.home") + File.separator +"Downloads"
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
import omero.gateway.model.FileAnnotationData
import omero.gateway.model.TagAnnotationData
import omero.gateway.model.MapAnnotationData
import omero.gateway.model.ExperimenterData
import omero.gateway.model.TextualAnnotationData
import omero.gateway.model.ROIData
import omero.model.RoiAnnotationLink
import omero.model.RoiAnnotationLinkI
import omero.gateway.model.RatingAnnotationData

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