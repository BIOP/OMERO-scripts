"""
This script gets ROIs from an image, replace its comment and update the OMERO object

To create new ROIs, you can have a look here : https://docs.openmicroscopy.org/omero/5.6.0/developers/Python.html#rois
"""

from omero.gateway import BlitzGateway
from omero.rtypes import rstring
import traceback

comment_to_remove = ""  # leave empty to remove any comment anyway
new_comment = "test2"
imageId = 3


def run_script():
    host = "localhost"
    conn = BlitzGateway("username", "password", host=host, port=4064, secure=True)
    conn.connect()

    if conn.isConnected():
        print(f"Connected to {host}")
        try:
            conn.SERVICE_OPTS.setOmeroGroup(-1)
            roi_service = conn.getRoiService()
            update_service = conn.getUpdateService()

            # getting ROIs attached to an image
            result = roi_service.findByImage(imageId, None,  conn.SERVICE_OPTS)
            # loop on the ROIs
            for roi in result.rois:
                # loop on all shapes within the ROI
                for s in roi.copyShapes():
                    # Find and update the comment
                    if comment_to_remove == "" or (s.getTextValue() and s.getTextValue().getValue() == comment_to_remove):
                        print(f"Update comment from '{s.getTextValue().getValue()}' to '{new_comment}'")
                        s.setTextValue(rstring(new_comment))
                        roi = update_service.saveAndReturnObject(roi)

        except Exception as e:
            print(e)
            traceback.print_exc()
        finally:
            conn.close()
            print(f"Disconnect from {host}")


if __name__ == "__main__":
    run_script()
