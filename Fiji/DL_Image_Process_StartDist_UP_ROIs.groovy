#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=true) PASSWORD
#@Long(label="ID", value=119273) id
#@String(label="Object", choices={"image","dataset","project","well","plate","screen"}) object_type
#@Boolean showImages
#@Boolean isDeleteExistingROIs
#@Boolean isSendNewROIs

#@CommandService command
#@Output labels
#@RoiManager rm
#@ResultsTable rt


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
 *  - omero_ij : https://github.com/ome/omero-insight/releases/download/v5.7.0/omero_ij-5.7.0-all.jar
 *  - simple-omero-client : https://github.com/GReD-Clermont/simple-omero-client
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
 */


IJ.run("Close All", "");
rm.reset()
rt.reset()

Client user_client = new Client();

user_client.connect("omero-server.epfl.ch", 4064, USERNAME, PASSWORD.toCharArray() );
println "Connection to Omero : Success"

try{
	
	switch ( object_type ){
		case "image":	
			processImage( 	user_client, user_client.getImage(id) )
			break	
		case "dataset":
			processDataset( user_client, user_client.getDataset(id) )
			break
		case "project":
			processProject( user_client, user_client.getProject(id) )
			break
		case "well":
			processWell( 	user_client, user_client.getWells(id) )
			break
		case "plate":
			processPlate( 	user_client, user_client.getPlates(id))
			break
		case "screen":
			processScreen( 	user_client, user_client.getScreens(id))
			break
	}
} finally{
	user_client.disconnect()
	println "Disconnection to Omero , user: Success"
}

println "processing of "+object_type+", id "+id+": DONE !"
return


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
		image_wpr.getROIs(user_client).each{ user_client.delete(it) }
	}
	else {
		println "Loading existing OMERO-ROIs"
		new ROIWrapper().toImageJ(image_wpr.getROIs( user_client) ).each{rm.addRoi(it)}
	}
	
	// do the processing here 
	println "Image Processing & Analysis : Start"
	ipas(imp)
	println "Image Processing & Analysis : End"
					
	// send ROIs to Omero
	if (isSendNewROIs){
		println "New ROIs uploading to OMERO"
		new ROIWrapper().fromImageJ(rm.getRoisAsArray() as List).each{ image_wpr.saveROI(user_client , it)	}		
	}
	
}

def processDataset( user_client, dataset_wpr ){
	dataset_wpr.getImages(user_client).each{ img_wpr ->
		processImage(user_client , img_wpr)
	}
}

def processProject( user_client, project_wpr ){
	project_wpr.getDatasets().each{ dataset_wpr ->
		processDataset(user_client , dataset_wpr)
	}
}

def processWell(user_client, well_wpr_list){		
	well_wpr_list.each{ well_wpr ->				
		well_wpr.getWellSamples().each{			
			processImage(user_client, it.getImage())		
		}
	}	
}

def processPlate(user_client, plate_wpr_list){
	plate_wpr_list.each{ plate_wpr ->	
		processWell(user_client, plate_wpr.getWells(user_client))
	} 
}

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
