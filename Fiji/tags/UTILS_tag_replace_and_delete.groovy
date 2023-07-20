#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@String(label="Name of the tag to replace") tagToDeleteStr
#@Boolean(label="Case insensitive ?", value=true) caseInsensitive
#@String(label="Name of the new tag") newTagStr
#@Boolean(label="The new tag already exists on OMERO ?", value=true) alreadyExists
#@String(choices={"Do nothing", "Replace only", "Replace and delete"}, value="Do nothing", style="radioButtonHorizontal", persist=false) mode
#@Boolean(label="Send CSV report ?", value=true) toCsv
#@File(label="Path to save CSV report",style="directory",required=false) path

/* 
 * == INPUTS ==
 *  - credentials 
 *  - name of the tag to replace
 *  - name of the new tag
 *  - Case insensitive ? true if you want to delete like "DAPI", "dapi", "dApi"... in one run. False otherwise
 *  - tag already exists one OMERO ? true if the NEW TAG already exists on OMERO. False otherwise
 *  - mode
 *  	- do Nothing : do not replace the old tag by the new neither delete the old tag (use it if you only want to generate a report)
 *  	- Replace only : replace the old tag by the new one for all images/containers/folders linked to the old tag BUT DOES NOT DELETE the old tag
 *  	- Replace nd delete : replace the old tag by the new one for all images/containers/folders linked to the old tag and delete the old tag
 *  - send CSV report ? true to get a report of which image/container are affected by tag replacement
 *  - path to save CSV report : path to the folder where to save the report
 * 
 * == OUTPUTS ==
 *  - replace an old tag by a new one
 *  - delete the old tag
 *  - generate a csv report of the tag replacement
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
 */

if(toCsv && !path.exists()){
	println "The path you enter does not exists. Please check it"
	return
}


// Connection to server
host = "omero-server.epfl.ch"
port = 4064

Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())

if (user_client.isConnected()){
	println "\nConnected to "+host
	
	try{
		println "You choose mode '"+mode+"'"
		def foundRepoToUpdate
		def tagToRepoMap = new HashMap<>()
		// get all group tags
		def groupTags = user_client.getTags()
		
		// check if the tab to delete exists or not
		def tagsToDelete
		if(caseInsensitive)
			tagsToDelete = groupTags.findAll{it.getName().equalsIgnoreCase(tagToDeleteStr)}
		else
			tagsToDelete = groupTags.findAll{it.getName().equals(tagToDeleteStr)}
			
		if(!tagsToDelete.isEmpty()){
			print "Found following tags to delete"
			tagsToDelete.each{print " | '"+it.getName()+"'"}
			print"\n"

			// check if the new tag already exists. Create it otherwise or return null if the tag should exist
			def newTag = groupTags.find{it.getName().equals(newTagStr)} ?: (alreadyExists ? null : new TagAnnotationWrapper(new TagAnnotationData(newTagStr)))
	
			if(newTag != null){
				tagsToDelete.each{tagToDelete->
					def tagToDeleteName = tagToDelete.getName()
					println "******* Begin with tag '"+tagToDeleteName+"'...********"
					List<DataObject> repoList = new ArrayList<>()
					
					// get all the images / containers / folders linked to the tag to delete
					def imgList = tagToDelete.getImages(user_client)
					def datasetList = tagToDelete.getDatasets(user_client)
					def projectList = tagToDelete.getProjects(user_client)
					def wellList = tagToDelete.getWells(user_client)
					def plateList = tagToDelete.getPlates(user_client)
					def screenList = tagToDelete.getScreens(user_client)
					def plateAcquisitionList = tagToDelete.getPlateAcquisitions(user_client)
					def folderList = tagToDelete.getFolders(user_client)
					
					if(!mode.equalsIgnoreCase("Do nothing")){
						// replace the old tag by the new one on all images / containers / folders
						(newTag, foundRepoToUpdate) = loopOverRepo(user_client, imgList, tagToDelete, newTag)
						if(!foundRepoToUpdate)
							println("No images linked to tag '"+tagToDeleteName+"'")	
						
						(newTag, foundRepoToUpdate) = loopOverRepo(user_client, datasetList, tagToDelete, newTag)
						if(!foundRepoToUpdate)
							println("No datasets linked to tag '"+tagToDeleteName+"'")
						
						(newTag, foundRepoToUpdate) = loopOverRepo(user_client, projectList, tagToDelete, newTag)
						if(!foundRepoToUpdate)
							println("No projects linked to tag '"+tagToDeleteName+"'")

						(newTag, foundRepoToUpdate) = loopOverRepo(user_client, wellList, tagToDelete, newTag)
						if(!foundRepoToUpdate)
							println("No wells linked to tag '"+tagToDeleteName+"'")

						(newTag, foundRepoToUpdate) = loopOverRepo(user_client, plateList, tagToDelete, newTag)
						if(!foundRepoToUpdate)
							println("No plates linked to tag '"+tagToDeleteName+"'")

						(newTag, foundRepoToUpdate) = loopOverRepo(user_client, screenList, tagToDelete, newTag)
						if(!foundRepoToUpdate)
							println("No screens linked to tag '"+tagToDeleteName+"'")

						(newTag, foundRepoToUpdate) = loopOverRepo(user_client, plateAcquisitionList, tagToDelete, newTag)
						if(!foundRepoToUpdate)
							println("No plate aquisitions linked to tag '"+tagToDeleteName+"'")

						(newTag, foundRepoToUpdate) = loopOverRepo(user_client, folderList, tagToDelete, newTag)
						if(!foundRepoToUpdate)
							println("No folders linked to tag '"+tagToDeleteName+"'")
					}
					
					// prepare maps to send a report
					if(toCsv){
						repoList.addAll(imgList)
						repoList.addAll(datasetList)
						repoList.addAll(projectList)
						repoList.addAll(wellList)
						repoList.addAll(plateList)
						repoList.addAll(screenList)
						repoList.addAll(plateAcquisitionList)
						repoList.addAll(folderList)
						tagToRepoMap.put(tagToDelete, repoList)
						println "Add new entries in the CSV report"
					}
					
					if(mode.equalsIgnoreCase("Replace and delete")){
						// delete the tag
						print "!!!!!! Delete tag '"+tagToDeleteName+"'..."
						user_client.delete(tagToDelete)
						println "Done !!!!!!!!"
					}
				}
				
				// send the report
				if(toCsv){
					println "Create the CSV report..."
					makeReport(tagToRepoMap, newTag, path, user_client)
				}
				
			} else {
				println "The new tag '"+newTagStr+"' does not exist on OMERO"
			}			
		} else {
			println "The tag to delete '"+tagToDeleteStr+"' does not exist on OMERO"
		}
		
	} finally{
		user_client.disconnect()
		println "Disconnected "+host
	}
	
	return
	
} else {
	println "Not able to connect to "+host
}




/**
 * Parse the repoList to replace the old tag by the new one
 */
def loopOverRepo(user_client, repoWprList, tagToDelete, newTag){
	// check if the tag has some links
	if(!repoWprList.isEmpty()){
		def className = repoWprList.get(0).getClass().getSimpleName()
		
		repoWprList.each{wpr->
			// only link the tag if it not already linked
			wpr.linkIfNotLinked(user_client, newTag)
			
			// update the TagAnnotationWrapper with the one from OMERO
			if(newTag.getId() < 0){
				newTag = user_client.getTags(newTag.getName()).get(0)
			}
			
			// unlink the tag from the repo
			wpr.unlink(user_client, tagToDelete)
			println className+" "+wpr.getName()+" : Add tag '"+newTag.getName()+"' and unlink tag '"+tagToDelete.getName()+"'"
		}
		return [newTag, true]
	} else{
		return [newTag, false]
	}
}




/**
 * Create a CSV report containing information about images / containers / folders 
 * that are/will be affected by the tag replacement
 */
def makeReport(tagToRepoMap, newTag, path, user_client){
	String content = ""
	
	// define the header
	content += "Repository type,Repository id,Repository name,Repository owner,Tag to delete,New tag\n"
	def experimenterMap = new HashMap<>()
	
	// loop on tags to delete
	tagToRepoMap.each{ tag, listOfRepo->
		// loop on all linked repos
		listOfRepo.each{repo ->
			def className = repo.getClass().getSimpleName()
			def repoName = repo.getName()
			def repoId = repo.getId()
			def repoOwnerId = repo.getOwner().getId()
			def repoOwner
			
			// read owner name
			if(experimenterMap.containsKey(repoOwnerId))
				repoOwner = experimenterMap.get(repoOwnerId).getOmeName().getValue()
			else{
				def experimenter = user_client.getGateway().getAdminService(user_client.getCtx()).getExperimenter(repoOwnerId)
				experimenterMap.put(repoOwnerId, experimenter)
				repoOwner = experimenter.getOmeName().getValue()
			}
			
			// add entry
			content += className +","+repoId +","+repoName +","+repoOwner +","+tag.getName() +","+newTag.getName()+"\n"
		}
	}
	
	// save the report
	def name = "Replace_tags_summary_report"
	print "Save the report as '"+name+"' in "+path+"...."
	writeCSVFile(path, name, content)	
	println "Done !"
}




/**
 * Save a csv file in the given path, with the given name
 */
def writeCSVFile(path, name, fileContent){
	// create the file locally
    File file = new File(path.toString() + File.separator + name + ".csv");

    // write the file
    BufferedWriter buffer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
    buffer.write(fileContent + "\n");

    // close the file
    buffer.close();
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