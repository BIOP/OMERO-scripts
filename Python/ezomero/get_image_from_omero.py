"""
----------------------------------------------------------------
DESCRIPTION
This script opens a connection to OMERO, imports an image, and closes the connection
----------------------------------------------------------------
INPUTS
    * OMERO credentials
    * OMERO image IDs. To import multiple images, separate each ID with a coma
----------------------------------------------------------------
OUTPUTS
    * Show images
----------------------------------------------------------------
DEPENDENCIES
    * Python 3.8 or >
    * ezomero 1.2.1 or >
    * PySimpleGUI 4.60.55 or >
    * Matplotlib
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
import matplotlib.pyplot as plt


"""
Main method
"""
if __name__ == "__main__":
    layout = [[sg.Text("Username"), sg.Input(key='user')],
              [sg.Text('Password'), sg.InputText('', key='password', password_char='*')],
              [sg.Text('Image ID'), sg.Input('', key='ids')],
              [sg.Button('Ok'), sg.Button('Cancel')]]

    # Create the window
    window = sg.Window('Import image from OMERO', layout)
    event, values = window.read()

    # Finish up by removing from the screen
    window.close()

    if event == 'Ok':
        user = values.get('user')
        password = values.get('password')
        omero_image_ids = str(values.get('ids'))

        conn = ezomero.connect(user=user, password=password, group='',
                               host='omero-server.epfl.ch', port=4064, secure=True, config_path=None)

        if conn is not None and conn.isConnected():
            try:
                for idx, omero_image_id in enumerate(omero_image_ids.split(",")):

                    img_omero_obj, img_nparray = ezomero.get_image(conn, int(omero_image_id))
                    print("The image "+str(img_omero_obj.getId())+" has been imported from OMERO")

                    # the image is stored in the order TZYXC
                    plt.figure(idx)
                    plt.imshow(img_nparray[0][0])
                plt.show()
            finally:
                conn.close()

        else:
            print("ERROR: Not able to connect to OMERO server. Please check your credentials, group and hostname")
