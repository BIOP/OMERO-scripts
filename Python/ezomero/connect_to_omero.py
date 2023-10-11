"""
----------------------------------------------------------------
DESCRIPTION
This script opens a connection to OMERO, prints Hello, and closes the connection
----------------------------------------------------------------
INPUTS
    * OMERO credentials
----------------------------------------------------------------
OUTPUTS
    * Print "Hello"
----------------------------------------------------------------
DEPENDENCIES
    * Python 3.8 or >
    * ezomero 1.2.1 or >
    * pyautogui 0.9.54 or >
----------------------------------------------------------------
INSTALLATION
Open the script in PyCharm and run it
----------------------------------------------------------------
AUTHOR
Code written by Rémy Dornier, EPFL - SV - PTCEH - PTBIOP
2023.10.11
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
import pyautogui


"""
Main method to connect to OMERO
"""
if __name__ == "__main__":
    user = pyautogui.prompt(text='Username', title='OMERO')
    password = pyautogui.password(text='Password', title='OMERO', default='', mask='*')

    conn = ezomero.connect(user=user, password=password, group='your_group',
                           host='omero-server.epfl.ch', port=4064, secure=True, config_path=None)

    if conn is not None and conn.isConnected():
        try:
            # Do some stuff
            print("Hello")

        finally:
            conn.close()
    else:
        print("ERROR: Not able to connect to OMERO server. Please check your credentials, group and hostname")