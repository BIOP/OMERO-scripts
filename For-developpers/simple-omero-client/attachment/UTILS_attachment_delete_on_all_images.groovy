#@String(label="Host", value="omero-server.epfl.ch") host
#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@String(label="Object to process", choices={"image","dataset","project","well","plate","screen"}) object_type
#@Long(label="Object ID", value=119273) id
#@Long(label="ONLY FOR PLATES, Run ID to process (-1 for all)", value = -1) runId
#@Boolean(label="Also delete attachments from colleagues", value = false) deleteDataYouDoNotOwn
#@Boolean(label="Dry Run", value = false) dryRun


/* 
 * Code description
 * 
 * Deletes all attachements from all images, children of the select object.
 *  
 *  
 * Dependencies
 *  - Fiji update site OMERO 5.5-5.6
 *  - Fiji update site PTBIOP, with simple-omero-client
 * 
 * Author: Rémy Dornier, EPFL - PTBIOP 
 * Date: 01.09.2023
 * Version: 1.1.0
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
 * - 2023-09-01 : First version -- v1.0.0
 * - 2026-04-23 : Remove popup window -v1.1.0
 */

/**
 * Main. Connect to OMERO, delete attachments and disconnect from OMERO
 * 
 */
 
// Connection to server
port = 4064
Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())


if (user_client.isConnected()){
	println "Connected to "+host
	
	try{
		switch (object_type){
			case "image":	
				n = processImage(user_client, user_client.getImage(id))
				break	
			case "dataset":
				n = processDataset(user_client, user_client.getDataset(id))
				break
			case "project":
				n = processProject(user_client, user_client.getProject(id))
				break
			case "well":
				n = processWell(user_client, user_client.getWell(id))
				break
			case "plate":
				if(runId > 0){
					def listRuns = user_client.getPlate(id).getPlateAcquisitions().stream().filter(e->e.getId() == runId).collect(Collectors.toList())
					if(!listRuns.isEmpty()){
						processRun(user_client, listRuns.get(0))
					}else{
						println "[ERROR] There is no Run with Id "+runId+" under the plate "+id
					}
				}else{
					processPlate(user_client, user_client.getPlate(id))
				}
				break
			case "screen":
				n = processScreen(user_client, user_client.getScreen(id))
				break
		}
		println n + " attachments were deleted for "+object_type+ " "+id + " and its child"
		
	} finally {
		user_client.disconnect()
		println "Disconnected from "+host
	}
	
} else {
	println "Not able to connect to "+host
}
return


/**
 * Delete all the attachment from an object
 * BE CAREFUL : you will delete the attachment, not remove the attachment from the object. Meaning that every people that use this attachment will losse it.
 * 
 * inputs
 * 		user_client : OMERO client
 * 		repository_wpr : OMERO repository object (image, dataset, project, well, plate, screen)
 * 
 * */
def processAttachment(user_client, repository_wpr){
	println "Working on image "+repository_wpr.getName()+":"+repository_wpr.getId()
	
	// get the list of attachments
	def file_wpr_list = repository_wpr.getFileAnnotations(user_client)
	
	// if not attachments
	if(file_wpr_list.isEmpty()){
		println "No files attached to "+repository_wpr.getClass().getSimpleName()+" : "+repository_wpr.getId()
		return 0
	}
	
	// get admin user info
	def userId = user_client.getUser().getId()
	def exp = user_client.getGateway().getAdminService(user_client.getCtx()).getExperimenter(ownerRepoId);
	
	List<FileAnnotationWrapper> attachments_to_delete = []
	
	file_wpr_list.each{file_wpr->
		//file can be deleted because the file is owned by the logged-in user
		if (file_wpr.getOwner().getId() == userId){
				println file_wpr.getFileName() + " will be deleted"
				attachments_to_delete.add(file_wpr)
		} else {
			if(deleteDataYouDoNotOwn){
				if(file_wpr.canDelete()){
					println "File '"+file_wpr.getFileName() + "' is owned by '"+fileOwner.getOmeName().getValue()+"' and will be deleted"
					attachments_to_delete.add(file_wpr)
				}else{
					println "File '"+file_wpr.getFileName() + "' will NOT be deleted because you don't have the right to delete it"
				}
			}
		}
	}
	
	// delete attachments
	if(!dryRun && !attachments_to_delete.isEmpty()){
		println "Delete files..."
		user_client.delete((Collection<GenericObjectWrapper<?>>)attachments_to_delete)
	}
	return attachments_to_delete.size()
}


/**
 * Delete attachments
 * 
 * inputs
 * 		user_client : OMERO client
 * 		repository_wpr : OMERO repository object (image, dataset, project, well, plate, screen)
 * 
 * */
def processImage(user_client, image_wpr) {
	return processAttachment(user_client , image_wpr)
}


/**
 * get all images within a dataset
 * 
 * inputs
 * 	 	user_client : OMERO client
 * 		dataset_wpr : OMERO dataset
 * 
 * */
def processDataset( user_client, dataset_wpr ){
	def sizeDelAtt = 0
	dataset_wpr.getImages(user_client).each{ image_wpr ->
		sizeDelAtt += processImage(user_client , image_wpr)
	}
	
	return sizeDelAtt
}


/**
 * get all datasets within a project
 * 
 * inputs
 * 	 	user_client : OMERO client
 * 		project_wpr : OMERO project
 * 
 * */
def processProject(user_client, project_wpr){
	def sizeDelAtt = 0
	project_wpr.getDatasets().each{ dataset_wpr ->
		sizeDelAtt += processDataset(user_client, dataset_wpr)
	}
	
	return sizeDelAtt
}


/**
 * get all images within a well
 * 
 * inputs
 * 	 	user_client : OMERO client
 * 		well_wpr_listet_wpr : OMERO list of wells
 * 
 * */
def processWell(user_client, well_wpr_list){	
	def sizeDelAtt = 0	
	well_wpr_list.each{ well_wpr ->		
		well_wpr.getWellSamples().each{			
			sizeDelAtt += processImage(user_client, it.getImage())		
		}
	}	
	return sizeDelAtt
}


/**
 * get all images within a run
 * 
 * inputs
 * 	 	user_client : OMERO client
 * 		pa_wpr : OMERO plate acquisition wrapper
 * 
 * */
def processRun(user_client, pa_wpr){
	def sizeDelAtt = 0
	pa_wpr.getImages(user_client).each{ image_wpr ->
		sizeDelAtt += processImage(user_client, image_wpr)
	}
	
	return sizeDelAtt
}


/**
 * get all wells within a plate
 * 
 * inputs
 * 	 	user_client : OMERO client
 * 		plate_wpr_list : OMERO list of plates
 * 
 * */
def processPlate(user_client, plate_wpr_list){
	def sizeDelAtt = 0
	plate_wpr_list.each{ plate_wpr ->	
		sizeDelAtt += processWell(user_client, plate_wpr.getWells(user_client))
	} 
	return sizeDelAtt
}


/**
 * get all plates within a screen
 * 
 * inputs
 * 	 	user_client : OMERO client
 * 		screen_wpr_list : OMERO list of screens
 * 
 * */
def processScreen(user_client, screen_wpr_list){
	def sizeDelAtt = 0
	screen_wpr_list.each{ screen_wpr ->	
		sizeDelAtt += processPlate(user_client, screen_wpr.getPlates())
	} 
	return sizeDelAtt
}


/*
 * imports  
 */
import fr.igred.omero.*
import fr.igred.omero.repository.*
import fr.igred.omero.annotations.*
import javax.swing.JFrame;
import javax.swing.JOptionPane;