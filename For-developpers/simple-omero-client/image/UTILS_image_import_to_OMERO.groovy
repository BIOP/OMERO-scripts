#@String(label="Host", value="omero-server.epfl.ch") host
#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@File(label="Image path") imgPath
#@Long(label="Dataset ID", value=119273) id
#@Boolean(label="Show images") showImages


/* Code description
 *  
 * Send the given image to OMERO.
 * Can only import the image in a dataset, not a plate
 * 
 * 
 * Dependencies
 *  - Fiji update site OMERO 5.5-5.6
 *  - Fiji update site PTBIOP, with simple-omero-client
 * 
 * Author: Rémy Dornier, EPFL - PTBIOP 
 * Date: 2022.05.18
 * Version: 1.0.0
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

// 


/**
 * Main. Connect to OMERO, import an image on OMERO and disconnect from OMERO
 * 
 */

// Connection to server
port = 4064
Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())

if (user_client.isConnected()){
	println "Connected to "+host
	
	try{
		importImageOnOmero(user_client)
		println "Importation in OMERO of image "+imgPath.getName()+", in dataset "+user_client.getDataset(id).getName()+" (id "+id+") : DONE !"
		
	} finally{
		user_client.disconnect()
		println "Disconnected from "+host
	}
}else{
	println "Not able to connect to "+host
}
return


/**
 * Import an image on OMERO in the specified dataset/well
 * 
 * input
 * 		user_client : OMERO client
 */
def importImageOnOmero(user_client){
	// clear Fiji env
	IJ.run("Close All", "");
	
	// import an image on OMERO
	image_omeroID = user_client.getDataset(id).importImage(user_client, imgPath.toString())
		
	// print image location on OMERO
	image_omeroID.each{println "\n Image '"+  user_client.getImage(it).getName() +"' was uploaded to OMERO with \n - ID : "+ it +
	"\n - in dataset : " + user_client.getDataset(id).getName()+" (id : "+id+")"+
	"\n - in project : "+ user_client.getImage(it).getProjects(user_client).get(0).getName()+ " (id : "+ user_client.getImage(it).getProjects(user_client).get(0).getId() +")"}

	// Show the imported image
	if (showImages) IJ.run("Bio-Formats Importer", "open="+imgPath+" autoscale color_mode=Default rois_import=[ROI manager] view=Hyperstack stack_order=XYCZT");
}


/*
 * imports  
 */
import fr.igred.omero.*
import ij.*
import ij.plugin.*