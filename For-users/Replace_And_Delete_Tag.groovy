#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@String(label="Name of the tag to replace") tagToDeleteStr
#@Boolean(label="Case insensitive ?", value=true) caseInsensitive
#@String(label="Name of the new tag") newTagStr
#@String(choices={"Do nothing", "Replace only", "Replace and delete"}, value="Do nothing", style="radioButtonHorizontal", persist=false) mode

/* 
 * == INPUTS ==
 *  - credentials 
 *  - name of the tag to replace
 *  - name of the new tag
 *  - Case insensitive ? true if you want to delete like "DAPI", "dapi", "dApi"... in one run. False otherwise
 *  - mode
 *  	- do Nothing : do not replace the old tag by the new neither delete the old tag (use it if you only want to generate a report)
 *  	- Replace only : replace the old tag by the new one for all images/containers/folders linked to the old tag BUT DOES NOT DELETE the old tag
 *  	- Replace nd delete : replace the old tag by the new one for all images/containers/folders linked to the old tag and delete the old tag
 * 
 * == OUTPUTS ==
 *  - replace an old tags by the new one
 *  - delete the old tags
 *  - generate a csv report of the tag replacement in your Downloads folder
 * 
 * = DEPENDENCIES =
 *  - Fiji update site OMERO 5.5-5.6
 *  - simple-omero-client-5.14.0 or later : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by Rémy Dornier, EPFL - SV - PTECH - BIOP 
 * 20.07.2023
 * version v2.1
 * 
 * = COPYRIGHT =
 * © All rights reserved. ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP), 2023
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
 * - 2023-10-17 : Add popup message at the end of the script and if an error occurs while running
 * - 2023.11.07 : Improve popup message, improve CSV report and add IJ logs --v2.0
 * - 2024.05.07 : Trim tag to remove noisy spaces and fix bug when trying to replace a tag by the same one -- v2.1
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
OLD_TAG= "Old tag"
NEW_TAG = "New tag"
RP_TYPE = "Repository class"
RP_ID = "Repository id"
RP_NAME = "Repository name"
RP_OWNER = "Repository owner"
STS_NEW_TAG = "New tag added"
STS_OLD_TAG = "Old tag unlinked"
STS_DEL_OLD_TAG = "Old tag deleted"

if (user_client.isConnected()){
	IJLoggerInfo("OMERO","Connected to "+host)
	List<Map<String, String>> transferSummary = new ArrayList<>()
	
	try{
		IJLoggerInfo("Input", "You choose the option '"+mode+"'")
		def foundRepoToUpdate
		def tagToRepoMap = new HashMap<>()
		Map<Long,String> experimentersMap = new HashMap<>()
		
		// get all group tags
		def groupTags
		try{
			groupTags = user_client.getTags()
		}catch(Exception e){
			hasFailed = true
			message = "Tags from your group cannot be retrieved"
			IJLoggerError("OMERO", message)
			throw e
		}
		
		// check if the tag to delete exists or not
		def tagsToDelete
		if(caseInsensitive)
			tagsToDelete = groupTags.findAll{it.getName().trim().equalsIgnoreCase(tagToDeleteStr.trim())}
		else
			tagsToDelete = groupTags.findAll{it.getName().trim().equals(tagToDeleteStr.trim())}
			
		if(!tagsToDelete.isEmpty()){
			IJLoggerInfo("OMERO", "Found following tags to delete : " + tagsToDelete.stream().map(TagAnnotationWrapper::getName).collect(Collectors.toList()).join(" ; "))

			// check if the new tag already exists. Create it otherwise or return null if the tag should exist
			def newTag = new TagAnnotationWrapper(new TagAnnotationData(newTagStr.trim()))
			IJLoggerInfo("OMERO", "The old tag(s) will be replaced by tag '"+newTag.getName()+"'")
			
			tagsToDelete.each{tagToDelete->
				def tagToDeleteName = tagToDelete.getName()
				IJLoggerInfo("OMERO", "******* Begin with tag '"+tagToDeleteName+"'...********")
				
				// get all the images / containers / folders linked to the tag to delete
				def imgList = tagToDelete.getImages(user_client)
				def datasetList = tagToDelete.getDatasets(user_client)
				def projectList = tagToDelete.getProjects(user_client)
				def wellList = tagToDelete.getWells(user_client)
				def plateList = tagToDelete.getPlates(user_client)
				def screenList = tagToDelete.getScreens(user_client)
				def plateAcquisitionList = tagToDelete.getPlateAcquisitions(user_client)
				def folderList = tagToDelete.getFolders(user_client)
				def tmpTransferSummary = []
				
				if(!mode.equalsIgnoreCase("Do nothing")){
					// replace the old tag by the new one on all images / containers / folders
					(newTag, repoSummaryList) = loopOverRepo(user_client, imgList, tagToDelete, newTag, experimentersMap)
					if(repoSummaryList.isEmpty())
						IJLoggerWarn("OMERO","No images linked to tag '"+tagToDeleteName+"'")
					tmpTransferSummary.addAll(repoSummaryList)
					
					(newTag, repoSummaryList) = loopOverRepo(user_client, datasetList, tagToDelete, newTag, experimentersMap)
					if(repoSummaryList.isEmpty())
						IJLoggerWarn("OMERO","No datasets linked to tag '"+tagToDeleteName+"'")
					tmpTransferSummary.addAll(repoSummaryList)
					
					(newTag, repoSummaryList) = loopOverRepo(user_client, projectList, tagToDelete, newTag, experimentersMap)
					if(repoSummaryList.isEmpty())
						IJLoggerWarn("OMERO","No projects linked to tag '"+tagToDeleteName+"'")
					tmpTransferSummary.addAll(repoSummaryList)

					(newTag, repoSummaryList) = loopOverRepo(user_client, wellList, tagToDelete, newTag, experimentersMap)
					if(repoSummaryList.isEmpty())
						IJLoggerWarn("OMERO","No wells linked to tag '"+tagToDeleteName+"'")
					tmpTransferSummary.addAll(repoSummaryList)

					(newTag, repoSummaryList) = loopOverRepo(user_client, plateList, tagToDelete, newTag, experimentersMap)
					if(repoSummaryList.isEmpty())
						IJLoggerWarn("OMERO","No plates linked to tag '"+tagToDeleteName+"'")
					tmpTransferSummary.addAll(repoSummaryList)

					(newTag, repoSummaryList) = loopOverRepo(user_client, screenList, tagToDelete, newTag, experimentersMap)
					if(repoSummaryList.isEmpty())
						IJLoggerWarn("OMERO","No screens linked to tag '"+tagToDeleteName+"'")
					tmpTransferSummary.addAll(repoSummaryList)

					(newTag, repoSummaryList) = loopOverRepo(user_client, plateAcquisitionList, tagToDelete, newTag, experimentersMap)
					if(repoSummaryList.isEmpty())
						IJLoggerWarn("OMERO","No plate aquisitions linked to tag '"+tagToDeleteName+"'")
					tmpTransferSummary.addAll(repoSummaryList)

					(newTag, repoSummaryList) = loopOverRepo(user_client, folderList, tagToDelete, newTag, experimentersMap)
					if(repoSummaryList.isEmpty())
						IJLoggerWarn("OMERO","No folders linked to tag '"+tagToDeleteName+"'")
					tmpTransferSummary.addAll(repoSummaryList)
				}
				
				// delete the tag only if it is not the same tag as the one added
				def tagDeleted = false
				if(mode.equalsIgnoreCase("Replace and delete") && tagToDelete.getId() != newTag.getId()){
					try{
						user_client.delete(tagToDelete)
						IJLoggerInfo("OMERO", "Tag '"+tagToDeleteName+"' deleted !")
						tagDeleted = true
					}catch(Exception e){
						hasSilentlyFailed = true
						message = "Cannot delete tag '"+tagToDeleteName+"'. You don't have the permission to delete this tag"						
						IJLoggerError("OMERO", message)
						IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e))
					}
				}

				tmpTransferSummary.each{repoMap->
					repoMap.put(OLD_TAG, tagToDeleteName)
					repoMap.put(NEW_TAG, newTag.getName())
				 	repoMap.put(STS_DEL_OLD_TAG, (tagDeleted ? "Yes": "No"))					
				}
				transferSummary.addAll(tmpTransferSummary)
			}
			
			if(hasSilentlyFailed)
				message = "The script ended with some errors."
			else 
				message = "The tags have been successfully replaced."
		} else {
			message = "The tag to delete '"+tagToDeleteStr+"' does not exist on OMERO."
			IJLoggerWarn("OMERO", message)
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
 * Parse the repoList to replace the old tag by the new one
 */
def loopOverRepo(user_client, repoWprList, tagToDelete, newTag, experimenterMap){
	List<Map<String, String>> repoSummary = new ArrayList<>()
	
	// check if the tag has some links
	if(!repoWprList.isEmpty()){
		def className = repoWprList.get(0).getClass().getSimpleName()
		
		for(GenericRepositoryObjectWrapper wpr : repoWprList){
			Map<String, String> repoSummaryMap = summarizeRepo(user_client, wpr, className, experimenterMap)

			try{
				// only link the tag if it not already linked
				wpr.linkIfNotLinked(user_client, newTag)
				IJLoggerInfo("OMERO", "Tag '" + newTag.getName() + "' linked to '" + className + " " + wpr.getName() +"'")
				repoSummaryMap.put(STS_NEW_TAG, "Yes")
			}catch(Exception e){
				hasSilentlyFailed = true
				message = "Cannot link tag '" + newTag.getName() + "' to '" + className + " " + wpr.getName() +"'. The old tag is not removed."				
				IJLoggerError("OMERO", message)
				IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e))
				continue
			}
			
			// update the TagAnnotationWrapper with the one from OMERO
			if(newTag.getId() < 0){
				newTag = user_client.getTags(newTag.getName()).reverse().get(0)
			}
			
			// unlink the tag from the repo if the tag is not the same as the one we've just linked
			if(newTag.getId() != tagToDelete.getId()){
				try{
					wpr.unlink(user_client, tagToDelete)
					IJLoggerInfo("OMERO", "Tag '" + tagToDelete.getName() + "' unlinked from '" + className + " " + wpr.getName() +"'")
					repoSummaryMap.put(STS_OLD_TAG, "Yes")
				}catch(Exception e){
					hasSilentlyFailed = true
					message = "Cannot unlink tag '" + tagToDelete.getName() + "' from '" + className + " " + wpr.getName() +"'"				
					IJLoggerError("OMERO", message)
					IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e))
				}
			}
			repoSummary.add(repoSummaryMap)
		}
	}
	return [newTag, repoSummary]
}


/**
 * create a summary map of the current repo
 */
def summarizeRepo(user_client, repo, className, experimenterMap){
	Map<String, String> repoSummaryMap = new HashMap<>()
	repoSummaryMap.put(RP_TYPE, className)
	repoSummaryMap.put(RP_ID, repo.getId())	
	repoSummaryMap.put(RP_NAME, repo.getName())
	def repoOwnerId = repo.getOwner().getId()

	// read owner name
	def repoOwner
	if(experimenterMap.containsKey(repoOwnerId))
		repoOwner = experimenterMap.get(repoOwnerId)
	else{
		repoOwner = user_client.getUser(repoOwnerId).getUserName()
		experimenterMap.put(repoOwnerId, repoOwner)
	}
	
	repoSummaryMap.put(RP_OWNER, repoOwner + ":"+repoOwnerId)
	
	return repoSummaryMap
}



/**
 * Create the CSV report from all info cleecting during the processing
 */
def generateCSVReport(transferSummaryList){
	// define the header
	String header = RP_TYPE + "," + RP_ID + "," + RP_NAME + "," + RP_OWNER + "," + OLD_TAG + "," + NEW_TAG + "," +
	  STS_OLD_TAG + "," + STS_NEW_TAG + "," + STS_DEL_OLD_TAG

	String statusOverallSummary = ""

	transferSummaryList.each{imgSummaryMap -> 
		String statusSummary = ""
		
		// For keys that should always exist
		statusSummary += imgSummaryMap.get(RP_TYPE)+","
		statusSummary += imgSummaryMap.get(RP_ID)+","
		statusSummary += imgSummaryMap.get(RP_NAME)+","
		statusSummary += imgSummaryMap.get(RP_OWNER)+","
		statusSummary += imgSummaryMap.get(OLD_TAG)+","
		statusSummary += imgSummaryMap.get(NEW_TAG)+","
		
		
		// in case of error, the results for that key is failed
		if(imgSummaryMap.containsKey(STS_OLD_TAG))
			statusSummary += imgSummaryMap.get(STS_OLD_TAG)+","
		else
			statusSummary += "No,"

		// Nothing to add if there is no error
		if(imgSummaryMap.containsKey(STS_NEW_TAG))
			statusSummary += imgSummaryMap.get(STS_NEW_TAG)+","
		else
			statusSummary += " No,"
			
		statusSummary += imgSummaryMap.get(STS_DEL_OLD_TAG)
		
		statusOverallSummary += statusSummary + "\n"
	}
	String content = header + "\n"+statusOverallSummary
					
	// save the report
	def name = getCurrentDateAndHour()+"_Replace_"+tagToDeleteStr+"_by_"+newTagStr+"_tag"
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
import omero.gateway.model.TagAnnotationData;
import omero.gateway.model.FileAnnotationData
import omero.gateway.model.ExperimenterData
import omero.model.IObject
import omero.gateway.model.DataObject
import java.util.stream.Collectors;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import ij.IJ;
import javax.swing.JOptionPane;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections