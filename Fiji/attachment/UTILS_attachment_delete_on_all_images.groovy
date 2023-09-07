#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@Long(label="ID", value=119273) id
#@String(label="Object", choices={"image","dataset","project","well","plate","screen"}) object_type


/* 
 * == INPUTS ==
 *  - credentials 
 *  - id
 *  - object type
 * 
 * == OUTPUTS ==
 *  - deletion of the attachment on OMERO
 * 
 * = DEPENDENCIES =
 *  - Fiji update site OMERO 5.5-5.6
 *  - simple-omero-client-5.14.2 : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by Rémy Dornier, EPFL - SV -PTECH - BIOP 
 * 01.09.2023
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
 * - 2023-09-01 : First version -- v1
 */

/**
 * Main. Connect to OMERO, delete attachments and disconnect from OMERO
 * 
 */
 
// Connection to server
host = "omero-server.epfl.ch"
port = 4064

Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())

deleteDataYouDoNotOwn = false
alreadyAskConfirmation = false

if (user_client.isConnected()){
	println "\nConnected to "+host
	
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
				n = processPlate(user_client, user_client.getPlate(id))
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
	// get the list of attachments
	def file_wpr_list = repository_wpr.getFileAnnotations(user_client)
	
	// if not attachments
	if(file_wpr_list.isEmpty()){
		println "No files attached to "+repository_wpr.getClass().getSimpleName()+" : "+repository_wpr.getId()
		return 0
	}
	
	// get admin user info
	def userId = user_client.getUser().getId()
	def ownerRepoId = repository_wpr.getOwner().getId()
	def groupId = repository_wpr.asDataObject().getGroupId()
	def exp = user_client.getGateway().getAdminService(user_client.getCtx()).getExperimenter(ownerRepoId);
	
	List<FileAnnotationWrapper> attachments_to_delete = []
	
	file_wpr_list.each{file_wpr->
		//file can be deleted because the file is owned by the logged-in user
		if (file_wpr.getOwner().getId() == userId){
				println file_wpr.getFileName() + " will be deleted"
				attachments_to_delete.add(file_wpr)
		} else {
			def fileOwner = user_client.getGateway().getAdminService(user_client.getCtx()).getExperimenter(file_wpr.getOwner().getId());
			if(alreadyAskConfirmation){			
				if(deleteDataYouDoNotOwn){
					// check if the file can be deleted
					if(file_wpr.canDelete()){
						println "File '"+file_wpr.getFileName() + "' is owned by '"+fileOwner.getOmeName().getValue()+"'. You choose to delete it"
						println "File '"+file_wpr.getFileName() + " will be deleted"
						attachments_to_delete.add(file_wpr)
					}else{
						println "File '"+file_wpr.getFileName() + "' will NOT be deleted because you don't have the right to delete it"
					}
				}else{
					println "File '"+file_wpr.getFileName() + "' will NOT be deleted because you choose to not delete data from '"+fileOwner.getOmeName().getValue()+"'"
				}
			}else{
				// ask the users if they want to delete data that are not owned by him, under the restriction that is allowed to do so
				def content = "Some files are owned by '"+fileOwner.getOmeName().getValue()+"'. Do you want to DEFINITIVELY delete all the files linked"+
				" to the "+object_type+":"+id+" (and its child) and owned by '"+fileOwner.getOmeName().getValue()+"' ? This action is irreversible"
				def title = "WARNING : delete data that you don't own"
				def choice = JOptionPane.showConfirmDialog(new JFrame(), content, title, JOptionPane.YES_NO_OPTION);
				alreadyAskConfirmation = true
				
				if(choice == JOptionPane.YES_OPTION){
					deleteDataYouDoNotOwn = true
					// check if the file can be deleted
					if(file_wpr.canDelete()){
						println "File '"+file_wpr.getFileName() + "' is owned by '"+fileOwner.getOmeName().getValue()+"'. You choose to delete it"
						println "File '"+file_wpr.getFileName() + " will be deleted"
						attachments_to_delete.add(file_wpr)
					}else{
						println "File '"+file_wpr.getFileName() + "' will NOT be deleted because you don't have the right to delete it"
					}
				}else{
					println "File '"+file_wpr.getFileName() + "' will NOT be deleted because you choose to not delete data from '"+fileOwner.getOmeName().getValue()+"'"
				}
			}			
		}
	}
	
	// delete attachments
	if(!attachments_to_delete.isEmpty())
		user_client.delete((Collection<GenericObjectWrapper<?>>)attachments_to_delete)
	
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
def processImage(user_client, img_wpr) {
	
	return processAttachment(user_client , img_wpr)
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
	def dataset_table = null;
	def sizeDelAtt = 0
	dataset_wpr.getImages(user_client).each{ img_wpr ->
		sizeDelAtt += processImage(user_client , img_wpr)
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
		sizeDelAtt += processDataset(user_client , dataset_wpr)
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
