#@String (visibility=MESSAGE, value="OMERO connection", required=false) msg0
#@String(label="Host", value="omero-server.epfl.ch") host
#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=true) PASSWORD
#@String (visibility=MESSAGE, value="Setup object to process", required=false) msg1
#@String(label="Object to process", choices={"image","dataset","project","well","plate","screen"}) object_type
#@String(label="Object ID or object(s) URL", value=119273) ids
#@Boolean(label="Delete current ROIs", value=true) isDeleteExistingROIs
#@String (visibility=MESSAGE, value="Output", required=false) msg4
#@Boolean (label="Send new ROIs", value=true) isSendNewROIs
#@Boolean (label="Show images", value=true) showImages

#@CommandService command
#@Output labels
#@RoiManager rm
#@ResultsTable rt


/* Code description
 *  
 * IPA template script which loads images on Fiji, 
 * delete existings ROIs on OMERO, run Stardist, and sends results back to OMERO
 * 
 * 
 * Dependencies
 *  - Fiji update site OMERO 5.5-5.6
 *  - Fiji update site PTBIOP, with simple-omero-client
 * 
 * Author: Romain Guiet & Rémy Dornier, EPFL - PTBIOP 
 * Date: 2022.04.06
 * Version: 1.2.0
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
 * - 2023-06-16 : Limits the number of call to the OMERO server + update the version of simple-omero-client to 5.12.3 + update documentation
 * - 2026.04.23 : Update UI 
 * - 2026.04.27 : Support parsing of URL instead of just an ID -v1.2.0
 */


IJ.run("Close All", "");
rm.reset()
rt.reset()

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
					processPlate(user_client, user_client.getPlates(id))
					break
				case "screen":
					processScreen(user_client, user_client.getScreens(id))
					break
			}
			println "processing of "+object_type+", id "+id+": DONE !"
		}

	} finally{
		user_client.disconnect()
		println "Disconnected from "+host
	}
} else {
	println "Not able to connect to "+host
}
return


/**
 * Parse OMERO URL to get the list of ids
 */
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
	    
		// get ids
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


/*
 *  Helpers function for the different "layers" Image,Dataset,Project,Well,Plate,Screen
 *  ipas(imp) is where the Image Processing & Analysis take part 
 */

def ipas(imp){

	rm.reset()
	
	def zProj_imp = ZProjector.run(imp,"avg");
	def channels = ChannelSplitter.split(zProj_imp);
	
	dapi_imp = channels[0].duplicate()
	stain_imp = channels[1].duplicate()
	
	command.run(StarDist2D.class, false, 	"input", dapi_imp,
				        "modelChoice", "Versatile (fluorescent nuclei)",
				        'probThresh',0.5,
				        'nmsThresh',0.4).get()
	
	meanInt = stain_imp.getStatistics().mean
	
	println "Nuclei_Nbr "+rm.getCount()
	println "mean_int "+meanInt
	
	rt.addRow()
	rt.addLabel(imp.getTitle() )
	rt.addValue("Nuclei_Nbr ",rm.getCount())
	rt.addValue("Mean_int ",meanInt)
	rt.show("Results")
}

/* OMERO helpers */

def processImage(user_client, image_wpr){
	// clear Fiji env
	IJ.run("Close All", "");
	rm.reset()
	
	println image_wpr.getName()
	ImagePlus imp = image_wpr.toImagePlus(user_client);
	if ( showImages ) imp.show()
	
	// delete existing ROIs
	
	if( isDeleteExistingROIs){
		println "Deleting existing OMERO-ROIs"
		def roisToDelete = image_wpr.getROIs(user_client)
		user_client.delete((Collection<GenericObjectWrapper<?>>)roisToDelete)
	}
	else {
		println "Loading existing OMERO-ROIs"
		ROIWrapper.toImageJ(image_wpr.getROIs( user_client) ).each{rm.addRoi(it)}
	}
	
	// do the processing here 
	println "Image Processing & Analysis : Start"
	ipas(imp)
	println "Image Processing & Analysis : End"
					
	// send ROIs to Omero
	if (isSendNewROIs){
		println "New ROIs uploading to OMERO"
		def roisToUpload = ROIWrapper.fromImageJ(rm.getRoisAsArray() as List)
		image_wpr.saveROIs(user_client , roisToUpload)	
	}
	
}


/**
 * process all images within a dataset
 * 
 * inputs
 * 	 	user_client : OMERO client
 * 		dataset_wpr : OMERO dataset
 * 
 * */
def processDataset( user_client, dataset_wpr ){
	dataset_wpr.getImages(user_client).each{ image_wpr ->
		processImage(user_client , image_wpr)
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
import ij.plugin.*
import ij.gui.PointRoi
import ch.epfl.biop.wrappers.cellpose.ij2commands.Cellpose_SegmentImgPlusAdvanced
import ch.epfl.biop.ij2command.*
import de.csbdresden.stardist.StarDist2D
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;