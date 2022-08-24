#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@Long(label="ID", value=119273) id
#@String(label="Object", choices={"image","dataset","project","well","plate","screen"}) object_type
#@Boolean(label="Show images") showImages


/* = CODE DESCRIPTION =
 * This is a template to interact with OMERO. 
 * User can specify the ID of an "image","dataset","project","well","plate","screen"
 * The selected object is then imported in FIJI
 * 
 * == INPUTS ==
 *  - host
 *  - port
 *  - credentials 
 *  - id
 *  - object type
 *  - display imported image or not
 * 
 * == OUTPUTS ==
 *  - open the image defined by id (or all images one after another from the dataset/project/... defined by id)
 *  - 
 * 
 * = DEPENDENCIES =
 *  - omero_ij : https://github.com/ome/omero-insight/releases/download/v5.7.0/omero_ij-5.7.0-all.jar
 *  - simple-omero-client : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by romain guiet & Rémy Dornier, EPFL - SV -PTECH - BIOP 
 * 04.07.2022
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
 */

/**
 * Main. Connect to OMERO, process images and disconnect from OMERO
 * 
 */

// Connection to server
host = "omero-server.epfl.ch"
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
				processPlate(user_client, user_client.getPlates(id))
				break
			case "screen":
				processScreen(user_client, user_client.getScreens(id))
				break
		}
		
	} finally{
		user_client.disconnect()
		println "Disonnected "+host
	}
	
	println "Importation in FIJI of "+object_type+", id "+id+": DONE !"
	return
	
}else{
	println "Not able to connect to "+host
}


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
	IJ.run("Close All", "");
	
	// Print image information
	println "\n Image infos"
	println ("Image_name : "+image_wpr.getName() + " / id : "+ image_wpr.getId())
	def dataset_wpr_list = image_wpr.getDatasets(user_client)

	// if the image is part of a dataset
	if(!dataset_wpr_list.isEmpty()){
		dataset_wpr_list.each{println("dataset_name : "+it.getName()+" / id : "+it.getId())};
		image_wpr.getProjects(user_client).each{println("Project_name : "+it.getName()+" / id : "+it.getId())};
	}

	// TO DECOMMENT WHEN THE RELEASE 5.9.2 OF SIMPLE-OMERO-CLIENT IS AVAILABLE
	// IF YOU NEED THIS PART OF CODE FOR YOUR APPLICATION, PLEASE CONTACT REMY DORNIER
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


/**
 * Import all images from a dataset in Fiji
 * 
 * inputs
 * 		user_client : OMERO client
 * 		dataset_wpr : OMERO dataset
 * 
 * */
def processDataset( user_client, dataset_wpr ){
	dataset_wpr.getImages(user_client).each{ img_wpr ->
		processImage(user_client , img_wpr)
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
import fr.igred.omero.roi.*
import fr.igred.omero.repository.*
import fr.igred.omero.repository.PixelsWrapper
import fr.igred.omero.annotations.*
import fr.igred.omero.meta.*
import omero.gateway.facility.MetadataFacility
import omero.gateway.facility.BrowseFacility
import omero.gateway.model.*
import omero.model.Experimenter.*
import omero.model.TagAnnotationDataI.*
import omero.gateway.model.TagAnnotationData.*
import omero.gateway.model.TagAnnotationData
import omero.gateway.model.FileAnnotationData.*
import omero.gateway.model.FileAnnotationData
import omero.gateway.model.RatingAnnotationData.*
import omero.gateway.model.RatingAnnotationData
import omero.model.ExperimenterI.*
import omero.model.enums.UnitsLength
import omero.model.enums.UnitsTemperature
import omero.gateway.model.ImageAcquisitionData
import ij.*
import omero.RLong;
import omero.model.*;