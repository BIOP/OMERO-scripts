#@String(label="Host", value="omero-server.epfl.ch") host
#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@String(label="Object to process", choices={"image","dataset","project","well","plate","screen"}) object_type
#@Long(label="Object ID", value=119273) id
#@Long(label="ONLY FOR PLATES, Run ID to process (-1 for all)", value = -1) runId
#@String (choices={"Images", "ROIs"}, style="radioButtonHorizontal", label="Target object", value="Images") processRois


/* Code description
 *
 * Deletes all KVPs attached to the target objects, under the select parent container.
 * 
 * 
 * Dependencies
 *  - Fiji update site OMERO 5.5-5.6
 *  - Fiji update site PTBIOP, with simple-omero-client
 * 
 * Author: Rémy Dornier, EPFL - PTBIOP 
 * Date: 2022.10.05
 * Version: 1.0.2
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
 * Hitsory
 * 	- 2023.06.16 : Limits the number of call to the OMERO server + update the version of simple-omero-client to 5.12.3 + remove unnecessary imports.
 * 	- 2026.04.01 : Update licence, handle ROIs and fix typos
 */


/**
 * Main. Connect to OMERO, delete key-values for the current object and disconnect from OMERO
 * 
 */
// Connection to server
port = 4064
Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())

if (user_client.isConnected()){
	println "\nConnected to "+host

	try{
		def n = 0

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
						n = processRun(user_client, listRuns.get(0))
					}else{
						println "[ERROR] There is no Run with Id "+runId+" under the plate "+id
					}
				}else{
					n = processPlate(user_client, user_client.getPlate(id))
				}
				break
			case "screen":
				n = processScreen(user_client, user_client.getScreen(id))
				break
		}
		println n + " key-value pairs deleted for "+processRois+" under "+object_type+ " "+id + (runId > 0 && object_type.equals("plate") ? ", run " + runId : "")
		
	} finally {
		user_client.disconnect()
		println "Disconnected from "+host
	}
} else {
	println "Not able to connect to "+host
}
return 

/**
 * Delete key-values
 * 
 * inputs
 * 		user_client : OMERO client
 * 		repository_wpr : OMERO repository object (image, dataset, project, well, plate, screen)
 * 
 * */
def processImage(user_client, image_wpr) {
	// delete kvp on ROIs attached to image
	if(processRois.equals("ROIs")){
		// load OMERO rois
		println "Loading ROIs for image " + image_wpr.getId() + " : " + image_wpr.getName()
		def omeroRois = image_wpr.getROIs(user_client)
		
		def kvpToDelete = []
		omeroRois.eachWithIndex{roiWrapper, idx ->
			kvpToDelete.addAll(roiWrapper.getMapAnnotations(user_client))
		}
		println "Deleting key-values on ROIs attached to image " + image_wpr.getId() + " : " + image_wpr.getName()
		user_client.delete((Collection<GenericObjectWrapper<?>>)kvpToDelete)
		return kvpToDelete.size()
	}else{
		// delete kvp on image only
		println "Deleting key-values on image " + image_wpr.getId() + " : " + image_wpr.getName()
		List<MapAnnotationWrapper> kvpToDelete = image_wpr.getMapAnnotations(user_client)		
		user_client.delete((Collection<GenericObjectWrapper<?>>)kvpToDelete)
		return kvpToDelete.size()
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
	def sizeKVP = 0
	dataset_wpr.getImages(user_client).each{ image_wpr ->
		sizeKVP += processImage(user_client , image_wpr)
	}
	
	return sizeKVP
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
	def sizeKVP = 0
	project_wpr.getDatasets().each{ dataset_wpr ->
		sizeKVP += processDataset(user_client , dataset_wpr)
	}
	
	return sizeKVP
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
	def sizeKVP = 0	
	well_wpr_list.each{ well_wpr ->				
		well_wpr.getWellSamples().each{			
			sizeKVP += processImage(user_client, it.getImage())		
		}
	}	
	return sizeKVP
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
	def sizeKVP = 0
	pa_wpr.getImages(user_client).each{ image_wpr ->	
		sizeKVP += processImage(user_client, image_wpr)
	} 
	return sizeKVP
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
	def sizeKVP = 0
	plate_wpr_list.each{ plate_wpr ->	
		sizeKVP += processWell(user_client, plate_wpr.getWells(user_client))
	} 
	return sizeKVP
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
	def sizeKVP = 0
	screen_wpr_list.each{ screen_wpr ->	
		sizeKVP += processPlate(user_client, screen_wpr.getPlates())
	} 
	return sizeKVP
}


/*
 * imports  
 */
import fr.igred.omero.*
import fr.igred.omero.repository.*
import fr.igred.omero.annotations.*
import java.util.stream.Collectors