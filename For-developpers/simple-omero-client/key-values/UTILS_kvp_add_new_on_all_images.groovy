#@String(label="Host", value="omero-server.epfl.ch") host
#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@String(label="Object to process", choices={"image","dataset","project","well","plate","screen"}) object_type
#@Long(label="Object ID", value=119273) id
#@Long(label="ONLY FOR PLATES, Run ID to process (-1 for all)", value = -1) runId
#@String(label="Key", value = "Key") key
#@String(label="Value", value = "Value") value
#@String(label="Namespace", required=false) namespace
#@String (choices={"Images", "ROIs"}, style="radioButtonHorizontal", label="Target object", value="Images") processRois

/* Code description 
 *
 * Adds new KVPs to all images, children of the select object, in a given namespace
 * 
 * 
 * Dependencies
 *  - Fiji update site OMERO 5.5-5.6
 *  - Fiji update site PTBIOP, with simple-omero-client
 * 
 * 
 * Author: Rémy Dornier, EPFL - PTBIOP 
 * Date: 01.09.2022
 * Version: 1.0.1
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
 * - 2023.06.19 : Remove unnecessary imports
 */

/**
 * Main. Connect to OMERO, add a key-value and disconnect from OMERO
 * 
 */
 
// select right namespace
NAMESPACE = namespace
if(namespace == null || namespace.isEmpty() || namespace.trim().isEmpty()){
	NAMESPACE = "openmicroscopy.org/omero/client/mapAnnotation"
}
 
// Connection to server
port = 4064
Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())


if (user_client.isConnected()){
	println "\nConnected to "+host
	
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
		println "Adding key-value pairs for "+processRois+" under "+object_type+ " "+id + (runId > 0 && object_type.equals("plate") ? ", run " + runId : "")
		
	} finally{
		user_client.disconnect()
		println "Disconnected from "+host
	}
}else{
	println "Not able to connect to "+host
}
return


def processImage(user_client, image_wpr) {
	// create a map of kvp
	def kvpMap = new HashMap<>()
	kvpMap.put(key, value)
	def listOfEntry = new ArrayList<>(kvpMap.entrySet())
	
	// delete kvp on ROIs attached to image
	if(processRois.equals("ROIs")){
		// load OMERO rois
		println "Loading ROIs for image " + image_wpr.getId() + " : " + image_wpr.getName()
		def omeroRois = image_wpr.getROIs(user_client)
		
		println "Adding key-values on ROIs from image " + image_wpr.getId() + " : " + image_wpr.getName()
		omeroRois.eachWithIndex{roiWrapper, idx ->
			def kvpList = []
			MapAnnotationWrapper mapAnnWrapper = new MapAnnotationWrapper((Collection<? extends Entry<String, String>>)listOfEntry)
			mapAnnWrapper.setNameSpace(NAMESPACE)
			kvpList.add(mapAnnWrapper)
			roiWrapper.link(user_client, (MapAnnotationWrapper[])kvpList.toArray())
		}
	}else{
		println "Adding key-values on image " + image_wpr.getId() + " : " + image_wpr.getName()
		def kvpList = []
		MapAnnotationWrapper mapAnnWrapper = new MapAnnotationWrapper((Collection<? extends Entry<String, String>>)listOfEntry)
		mapAnnWrapper.setNameSpace(NAMESPACE)
		kvpList.add(mapAnnWrapper)
		image_wpr.link(user_client, (MapAnnotationWrapper[])kvpList.toArray())
	}	
}


/**
 * Import all images from a dataset in Fiji
 * 
 * inputs
 * 		user_client : OMERO client
 * 		dataset_wpr : OMERO dataset
 * 
 * */
def processDataset( user_client, dataset_wpr ){
	dataset_wpr.getImages(user_client).each{ image_wpr ->
		processImage(user_client , image_wpr)
	}
}


/**
 * Import all images from a dataset in Fiji
 * 
 * inputs
 * 		user_client : OMERO client
 * 		dataset_wpr : OMERO dataset
 * 
 * */
def processProject( user_client, project_wpr ){
	project_wpr.getDatasets().each{ dataset_wpr ->
		processDataset(user_client , dataset_wpr)
	}
}


/**
 * Import all images from a well in Fiji
 * 
 * inputs
 * 		user_client : OMERO client
 * 		well_wpr_list : OMERO wells
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
 * Import all images from a screen in Fiji
 * 
 * inputs
 * 		user_client : OMERO client
 * 		screen_wpr_List : OMERO screens
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
import fr.igred.omero.annotations.*
import omero.model.NamedValue
import java.io.*;
import java.util.Collection
import java.util.Map.Entry