#@String(label="Username", value="dornier", persist=false) USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@Long(label="Image ID", value=119273) id


/* 
 * == INPUTS ==
 *  - credentials 
 *  - image id
 * 
 * == OUTPUTS ==
 *  - image hierarchy on OMERO
 * 
 * = DEPENDENCIES =
 *  - Fiji update site OMERO 5.5-5.6
 *  - simple-omero-client-5.9.2 or later : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by Rémy Dornier, EPFL - SV -PTECH - BIOP 
 * 01.09.2022
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

// Connection to server
host = "omero-server.epfl.ch"
port = 4064

Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())

if (user_client.isConnected()){
	println "\nConnected "+ host
	
	try{
		// Create the image Wrapper object
		ImageWrapper image_wpr = user_client.getImage(id)
		
		// Get the well continaing the image
		def well_wpr_list = image_wpr.getWells(user_client)
		
		if(!well_wpr_list.isEmpty()){
			def well_wpr = well_wpr_list.get(0)
			println ("Well_name : "+well_wpr.getName() +" / id : "+ well_wpr.getId())
		
			// Get the plate continaing the image
			def plate_wpr_list = image_wpr.getPlates(user_client)
			def plate_wpr = plate_wpr_list.get(0)
			println ("plate_name : "+plate_wpr.getName() + " / id : "+ plate_wpr.getId())
		
			// Get the screen continaing the image		
			def screen_wpr_list = image_wpr.getScreens(user_client)
			def screen_wpr = screen_wpr_list.get(0)
			println ("screen_name : "+screen_wpr.getName() + " / id : "+ screen_wpr.getId())
		}
		else{
			println "Warning : Your image is part of a dataset/project, not part of a plate/screen"
		}
		
	} finally {
		user_client.disconnect()
		println "Disonnected from "+host
	}
} else {
	println "Not able to connect to "+host
}

import fr.igred.omero.*
import fr.igred.omero.repository.*
import omero.gateway.model.DatasetData;
