#@String(label="Host", value="omero-server.epfl.ch") host
#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@String(label="Object to process", choices={"image","dataset","project","well","plate","screen"}) object_type
#@String(label="Object ID or object(s) URL", value=119273) ids
#@Long(label="ONLY FOR PLATES, Run ID to process (-1 for all)", value = -1) runId
#@Boolean(label="Show images", value=true) showImages


/* Code description
 *  
 * Imports all children images from the select object in Fiji
 * 
 * 
 * Dependencies
 *  - Fiji update site OMERO 5.5-5.6
 *  - Fiji update site PTBIOP, with simple-omero-client
 * 
 * Author: Rémy Dornier, EPFL - PTBIOP 
 * Date: 2022.07.04
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
 * - 2023.06.19 : Remove unnecessary imports
 * - 2026.04.23 : Support parsing of URL instead of just an ID -v1.1.0
 */

/**
 * Main. Connect to OMERO, process images and disconnect from OMERO
 * 
 */

// Connection to server
port = 4064
Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())


if (user_client.isConnected()){
	println "Connected to "+host
	
	try{
		def idList = []
		try{
			Long.parseLong(ids)
			idList.add(id)
		}catch (Exception e){
			idList = parseURL(ids)
		}
		
		idList.each{id ->
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
							n = processRun(user_client, listRuns.get(0))
						}else{
							println "[ERROR] There is no Run with Id "+runId+" under the plate "+id
						}
					}else{
						n = processPlate(user_client, user_client.getPlate(id))
					}
					break
				case "screen":
					processScreen(user_client, user_client.getScreens(id))
					break
			}
			println "Successfull importation in FIJI of "+object_type+", id "+id + (runId > 0 && object_type.equals("plate") ? ", run " + runId : "")
		}
	} finally{
		user_client.disconnect()
		println "Disconnected from "+host
	}
}else{
	println "Not able to connect to "+host
}
return


/**
 * Display image metadata and import the image in Fiji
 * 
 * inputs
 * 		user_client : OMERO client
 * 		image_wpr : OMERO image
 * 
 * */
def processImage(user_client, image_wpr){
	// clear Fiji env
	if (!showImages) IJ.run("Close All", "");
	
	// Print image information
	println "\n Image infos"
	println ("Image_name : "+image_wpr.getName() + " / id : "+ image_wpr.getId())
	def dataset_wpr_list = image_wpr.getDatasets(user_client)

	// if the image is part of a dataset
	if(!dataset_wpr_list.isEmpty()){
		dataset_wpr_list.each{println("dataset_name : "+it.getName()+" / id : "+it.getId())};
		image_wpr.getProjects(user_client).each{println("Project_name : "+it.getName()+" / id : "+it.getId())};
	}

	// if the image is part of a plate
	else {
		def well_wpr = image_wpr.getWells(user_client).get(0)
		println ("Well_name : "+well_wpr.getName() +" / id : "+ well_wpr.getId())
		
		def plate_wpr = image_wpr.getPlates(user_client).get(0)
		println ("plate_name : "+plate_wpr.getName() + " / id : "+ plate_wpr.getId())

		def screen_wpr = image_wpr.getScreens(user_client).get(0)
		println ("screen_name : "+screen_wpr.getName() + " / id : "+ screen_wpr.getId())
	}
	
	// Show the imported image
	ImagePlus imp = image_wpr.toImagePlus(user_client);
	if (showImages) imp.show()
}


def parseURL(url){
	def idList = []
	
	// Check that URL is correct
	if (url.contains("?show=")) {
	    def showPart = url.split("\\?show=")[1]
	    
	    // get everything after the |
	    def items = showPart.split("\\|")
	    
	    def results = []
	    
	    // Parse each element
	    items.each { item ->
	        def matcher = (item =~ /^([a-zA-Z]+)-(\d+)$/)
	        if (matcher.matches()) {
	            results << [
	                type: matcher.group(1),
	                id: matcher.group(2).toInteger()
	            ]
	        }
	    }

	    // Exemple d'accès direct aux IDs si besoin
	    
	    def type = results.collect { it.type }.unique()
	    if(type.size() == 1 && type.get(0).equalsIgnoreCase(object_type)){
	    	idList = results.collect { it.id }
	    } else {
	    	 println "The type of objects in the URL "+type+" does not match with the selected object type "+object_type
	    }
	
	} else {
	    println "The URL doesn't contain '?show='; it's not coming from OMERO."
	}
	return idList
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
 * Import all images from a plate in Fiji
 * 
 * inputs
 * 		user_client : OMERO client
 * 		plate_wpr_list : OMERO plates
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
import ij.*