#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@Long(label="ID", value=119273) id
#@String(label="Object", choices={"image","dataset","project","well","plate","screen"}) object_type
#@Boolean showImages
#@Boolean isDeleteExistingROIs
#@Boolean isSendNewROIs

#@ResultsTable rt
#@RoiManager rm
#@CommandService command


/* = CODE DESCRIPTION =
 * This is a template to interact with OMERO . 
 * User can specify the ID of an "image","dataset","project","well","plate","screen"
 * 
 * == INPUTS ==
 *  - credentials 
 *  - id 
 *  - object type
 * 
 * == OUTPUTS ==
 *  - open the image defined by id (or all images one after another from the dataset/project/... defined by id)
 *  - 
 * 
 * = DEPENDENCIES =
 *  - OMERO 5.5-5.6 update site on Fiji
 *  - simple-omero-client 5.12.3 : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by romain guiet, EPFL - SV -PTECH - BIOP 
 * 06.04.2022
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
 * - 2023-06-16 : Limits the number of call to the OMERO server + update the version of simple-omero-client to 5.12.3
 */


IJ.run("Close All", "");
rm.reset()
rt.reset()

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
		println "Processing of "+object_type+", id "+id+": DONE !"

	} finally {
		user_client.disconnect()
		println "Disonnected from "+host
	}
	
} else {
	println "Not able to connect to "+host
}



//*  ipas(imp) is where the Image Processing & Analysis take part 
def ipas(imp){
	// add the Image Processing & Analysis part here 
}


def processImage(user_client, img_wpr){
	// clear Fiji env
	IJ.run("Close All", "");
	rm.reset()
	
	println img_wpr.getName()
	ImagePlus imp = img_wpr.toImagePlus(user_client);
	if (showImages) imp.show()
	
	// delete existing ROIs
	
	if(isDeleteExistingROIs){
		println "Deleting existing OMERO-ROIs"
		def roisToDelete = img_wpr.getROIs(user_client)
		user_client.delete((Collection<GenericObjectWrapper<?>>)roisToDelete)
	} else {
		println "Loading existing OMERO-ROIs"
		ROIWrapper.toImageJ(img_wpr.getROIs( user_client) ).each{rm.addRoi(it)}
	}
	
	// do the processing here 
	println "Image Processing & Analysis : Start"
	ipas(imp)
	println "Image Processing & Analysis : End"
					
	// send ROIs to Omero
	if (isSendNewROIs){
		println "New ROIs uploading to OMERO"
		def roisToUpload = ROIWrapper.fromImageJ(rm.getRoisAsArray() as List)
		img_wpr.saveROIs(user_client , roisToUpload)
	}
	
}


/*
 *  Helpers function for the different "layers" Image,Dataset,Project,Well,Plate,Screen
 */

/**
 * process all images within a dataset
 * 
 * inputs
 * 	 	user_client : OMERO client
 * 		dataset_wpr : OMERO dataset
 * 
 * */
def processDataset( user_client, dataset_wpr ){
	dataset_wpr.getImages(user_client).each{ img_wpr ->
		processImage(user_client , img_wpr)
	}
}


/**
 * process all datasets within a project
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
 * process all images within a well
 * 
 * inputs
 * 	 	user_client : OMERO client
 * 		well_wpr_listet_wpr :  list of OMERO wells
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
 * process all wells within a plate
 * 
 * inputs
 * 	 	user_client : OMERO client
 * 		plate_wpr_list : List of OMERO plates
 * 
 * */
def processPlate(user_client, plate_wpr_list){
	plate_wpr_list.each{ plate_wpr ->	
		processWell(user_client, plate_wpr.getWells(user_client))
	} 
}


/**
 * process all plates within a screen
 * 
 * inputs
 * 	 	user_client : OMERO client
 * 		screen_wpr_list : List of OMERO screens
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
import ij.*