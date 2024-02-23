#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@String(label="Object to process", choices={"image","dataset"}) object_type
#@Long(label="Image ID", value=119273) id
#@Float(label="Pixel size X (um)", value=0.5) pxlSizeX
#@Float(label="Pixel size Y (um)", value=0.5) pxlSizeY



/* = CODE DESCRIPTION =
 * This is a template to interact with OMERO. 
 * Enter new pixel size values
 * 
 * == INPUTS ==
 *  - credentials 
 *  - id
 *  - object type
 *  - X and Y pixel size
 * 
 * == OUTPUTS ==
 *  - replace the current pixel size in X and Y on OMERO by the new ones.
 * 
 * = DEPENDENCIES =
 *  - Fiji update site OMERO 5.5-5.6
 *  - simple-omero-client-5.16.0 or later : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by romain guiet & Rémy Dornier, EPFL - SV - PTECH - BIOP 
 * 2024.02.23
 * 
 * = COPYRIGHT =
 * © All rights reserved. ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP), 20224
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
		}		
		
	} finally{
		user_client.disconnect()
		println "Disonnected from "+host
	}
	
}else{
	println "Not able to connect to "+host
}
return



/**
 * Modify the pixel size of an image
 * 
 * */
def processImage(user_client, image_wpr){
	// get the current pixel size
	def currentPixelSizeX = image_wpr.getPixels().getPixelSizeX().getValue()
	def currentPixelSizeY = image_wpr.getPixels().getPixelSizeY().getValue()
	
	// set the new pixel size
	def pixelSizeXLength = new LengthI(pxlSizeX, UnitsLength.MICROMETER)
	def pixelSizeYLength = new LengthI(pxlSizeY, UnitsLength.MICROMETER)
	image_wpr.getPixels().asDataObject().setPixelSizeX(pixelSizeXLength)
	image_wpr.getPixels().asDataObject().setPixelSizeY(pixelSizeYLength)
	
	// save the new pixel size
	user_client.save(image_wpr.asIObject())
	println "Pixel size modification - Image id "+id+" - Replace "+currentPixelSizeX+" x "+currentPixelSizeY+" by "+pxlSizeX+" x "+pxlSizeY+" - DONE !"
}



/**
 * Process the full images with the specified dataset
 * 
 * */
def processDataset(user_client, dataset_wpr){
	dataset_wpr.getImages(user_client).each{ img_wpr ->
		processImage(user_client , img_wpr)
	}
}


/*
 * imports  
 */
import fr.igred.omero.*
import fr.igred.omero.repository.*
import omero.model.LengthI
import omero.model.enums.UnitsLength
