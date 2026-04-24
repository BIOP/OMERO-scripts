#@String(label="Host", value="omero-server.epfl.ch") host
#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@String(label="Object to process", choices={"image","dataset","project","well","plate","screen"}) object_type
#@Long(label="Object ID", value=119273) id
#@Long(label="ONLY FOR PLATES, Run ID to process (-1 for all)", value = -1) runId
#@String(label="New Tag(s)", value = "new_tag1,new_tag2") USER_TAGS
#@String (choices={"Images", "ROIs"}, style="radioButtonHorizontal", label="Target object", value="Images") processRois


/* Code description 
 *
 * Adds new tags to all images, children of the select object.
 * Tags have to be comma-separated
 * 
 * 
 * Dependencies
 *  - Fiji update site OMERO 5.5-5.6
 *  - Fiji update site PTBIOP, with simple-omero-client
 * 
 * 
 * Author: Rémy Dornier, EPFL - PTBIOP 
 * Date: 2022.05.18
 * Version: 1.0.3
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
 * - 2023-10-17 : Add popup message at the end of the script and if an error occurs while running.
 * - 2023.11.06 : Remove popup messages from template
 * - 2026.04.23 : Update add tag method + run & Rois support -v1.0.3
 * 
 */


/**
 * Main. Connect to OMERO, process tags and disconnect from OMERO
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
				processImage(user_client, user_client.getImage(id))
				break	
			case "dataset":
				processDataset(user_client, user_client.getDataset(id))
				break
			case "project":
				processProject(user_client, user_client.getProject(id))
				break
			case "well":
				processWell(user_client, user_client.getWells(id))
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
				processScreen(user_client, user_client.getScreens(id))
				break
		}
		
		println "Tags '"+tags+"' have been successfully added on all images contained in "+object_type+ " "+id
		
	}finally{
		user_client.disconnect()
		println "Disconnected from "+host
	}	
}else{
	println "Not able to connect to "+host
}
return



/**
 * Add a list of tags to the specified image
 * 
 */
def saveTagsOnOmero(user_client, imageWrapper, tags){
	def tagsToAdd = []
	
	// get existing tags
	def groupTags = user_client.getTags()
	def imageTags = imageWrapper.getTags(user_client)
	
	// find if the tag to add already exists on OMERO. If yes, they are not added twice
	tags.each{tag->
		if(tagsToAdd.find{ it.getName().toLowerCase().equals(tag.toLowerCase()) } == null){
			// find if the requested tag already exists
			new_tag = groupTags.find{ it.getName().toLowerCase().equals(tag.toLowerCase()) } ?: new TagAnnotationWrapper(new TagAnnotationData(tag))
			
			// add the tag if it is not already the case
			imageTags.find{ it.getName().toLowerCase().equals(new_tag.getName().toLowerCase()) } ?: tagsToAdd.add(new_tag)
		}
	}
	imageWrapper.addTags(user_client, (TagAnnotationWrapper[])tagsToAdd.toArray())
}



/**
 * Add a new tags to the object.
 * 
 * inputs
 * 		user_client : OMERO client
 * 		wpr : OMERO object wrapper (image, dataset, project, well, plate, screen)
 * 
 * */
def processImage(user_client, image_wpr){
	def userTags = USER_TAGS.split(",")
		
	if(processRois.equals("ROIs")){
		// load OMERO rois
		println "Loading ROIs for image " + image_wpr.getId() + " : " + image_wpr.getName()
		def omeroRois = image_wpr.getROIs(user_client)

		println "Linking tags to ROIs..."
		omeroRois.each{roiWrapper ->
			saveTagsOnOmero(user_client, roiWrapper, userTags)
		}
	}else{	
		println "Linking tags to image..."
		saveTagsOnOmero(user_client, image_wpr, userTags)
	}
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
	dataset_wpr.getImages(user_client).each{ image_wpr ->
		processImage(user_client , image_wpr)
	}
}


/**
 * get all datasets within a project
 * 
 * inputs
 * 	 	user_client : OMERO client
 * 		project_wpr : OMERO project
 * 
 * */
def processProject( user_client, project_wpr ){
	project_wpr.getDatasets().each{ dataset_wpr ->
		processDataset(user_client , dataset_wpr)
	}
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
	well_wpr_list.each{ well_wpr ->				
		well_wpr.getWellSamples().each{			
			processImage(user_client, it.getImage())		
		}
	}	
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
	pa_wpr.getImages(user_client).each{ image_wpr ->	
		processImage(user_client, image_wpr)
	} 
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
	plate_wpr_list.each{ plate_wpr ->	
		processWell(user_client, plate_wpr.getWells(user_client))
	} 
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
	screen_wpr_list.each{ screen_wpr ->	
		processPlate(user_client, screen_wpr.getPlates())
	} 
}


/*
 * imports  
 */
import fr.igred.omero.*
import fr.igred.omero.repository.*
import fr.igred.omero.annotations.*
import omero.gateway.model.TagAnnotationData;