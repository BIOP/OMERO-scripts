#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@Long(label="Image ID", value=119273) id

#@RoiManager rm


/* = CODE DESCRIPTION =
 * This is a template to interact with OMERO. 
 * It reads ROIs attached to an image on OMERO and convert them to ImageJ ROIs 
 * 
 * == INPUTS ==
 *  - credentials 
 *  - id 
 * 
 * == OUTPUTS ==
 *  - ImageJ ROIS in the ROI manager and image display
 *  
 * 
 * = DEPENDENCIES =
 *  - OMERO 5.5-5.6 update site on Fiji
 *  - simple-omero-client 5.14.0 : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by Rémy Dornier, EPFL - SV -PTECH - BIOP 
 * 2023.06.30
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
 */


IJ.run("Close All", "");
rm.reset()

// Connection to server
host = "omero-server.epfl.ch"
port = 4064

Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())

if (user_client.isConnected()){
	println "\nConnected to "+host

	try{
		processImage(user_client, user_client.getImage(id))
		println "Processing of image, id "+id+": DONE !"
	} finally {
		user_client.disconnect()
		println "Disonnected from "+host
	}
	
} else {
	println "Not able to connect to "+host
}



def processImage(user_client, img_wpr){
	// clear Fiji env
	IJ.run("Close All", "");
	rm.reset()
	
	// convert imageWrapper to imagePlus
	println img_wpr.getName()
	ImagePlus imp = img_wpr.toImagePlus(user_client);
	imp.show()
	
	// load OMERO rois
	println "Loading existing OMERO-ROIs"
	def omeroRois = img_wpr.getROIs(user_client)
	
	// convert omero ROIs into ImageJ ROIs
	ROIWrapper.toImageJ(omeroRois).each{rm.addRoi(it)}
}


/*
 * imports  
 */
import fr.igred.omero.*
import fr.igred.omero.roi.*
import fr.igred.omero.repository.*
import ij.*