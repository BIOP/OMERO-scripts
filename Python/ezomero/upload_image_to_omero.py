"""
----------------------------------------------------------------
DESCRIPTION
This script opens a connection to OMERO, upload an image on OMERO, and closes the connection
----------------------------------------------------------------
INPUTS
    * OMERO credentials
    * Path of the image to import
    * OMERO Dataset IDs (target on OMERO)
----------------------------------------------------------------
OUTPUTS
    * Upload on OMERO
----------------------------------------------------------------
DEPENDENCIES
    * Python 3.8 or >
    * ezomero 1.2.1 or >
    * PySimpleGUI 4.60.55 or >
----------------------------------------------------------------
INSTALLATION
Open the script in PyCharm and run it
----------------------------------------------------------------
AUTHOR
Code written by Rémy Dornier, EPFL - SV - PTCEH - PTBIOP
2023.10.17
----------------------------------------------------------------
VERSION
v1.0.0
----------------------------------------------------------------
COPYRIGHT
 * © All rights reserved. ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP),
 * 2023
 *
 * Licensed under the BSD-3-Clause License:
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided
 * that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and
 *  the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and
 *  the following disclaimer
 *    in the documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or
 *     promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ----------------------------------------------------------------
"""

import ezomero
import PySimpleGUI as sg
import os

"""
Main method
"""
if __name__ == "__main__":
    layout = [[sg.Text("Username"), sg.Input(key='user')],
              [sg.Text('Password'), sg.InputText('', key='password', password_char='*')],
              [sg.Text('Image path'), sg.Input('', key='path')],
              [sg.Text('Dataset ID'), sg.Input('', key='id')],
              [sg.Button('Ok'), sg.Button('Cancel')]]

    # Create the window
    window = sg.Window('Upload image to OMERO', layout)
    event, values = window.read()

    # Finish up by removing from the screen
    window.close()

    if event == 'Ok':
        user = values.get('user')
        password = values.get('password')
        img_path = str(values.get('path'))
        dataset_id = int(values.get('id'))

        conn = ezomero.connect(user=user, password=password, group='',
                               host='omero-server.epfl.ch', port=4064, secure=True, config_path=None)

        if conn is not None and conn.isConnected():
            try:
                os.chdir(os.path.dirname(img_path))
                img_ids = ezomero.ezimport(conn, os.path.basename(img_path), dataset=dataset_id)
                print("The image has been uploaded on OMERO with id " + str(','.join(str(e) for e in img_ids)) +
                      " in dataset " + str(dataset_id))
            finally:
                conn.close()

        else:
            print("ERROR: Not able to connect to OMERO server. Please check your credentials, group and hostname")
