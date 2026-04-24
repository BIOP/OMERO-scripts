"""
This script gets ROIs from an image, replace its comment and update the OMERO object

To create new ROIs, you can have a look here : https://docs.openmicroscopy.org/omero/5.6.0/developers/Python.html#rois
"""

from omero.gateway import BlitzGateway

import traceback
import omero
import skimage as ski
from omero.rtypes import rstring, rint
from skimage.measure import find_contours
from skimage.filters import threshold_otsu


imageId = 43198


def run_script():
    host = "localhost"
    conn = BlitzGateway("username", "password", host=host, port=4064, secure=True)
    conn.connect()

    if conn.isConnected():
        print(f"Connected to {host}")
        try:
            conn.SERVICE_OPTS.setOmeroGroup(-1)
            update_service = conn.getUpdateService()

            image = conn.getObject("Image", imageId)

            coins = ski.data.coins()
            threshold_value = threshold_otsu(coins)
            contours = find_contours(coins)


            points = []
            for point in contours[60]:
                points.append(str(float(point[1])) + "," + str(float(point[0])))

            print(", ".join(points))
            points = ", ".join(points)
            # create a ROI with a single polygon, setting colors and lineWidth
            polygon = omero.model.PolygonI()
            polygon.theZ = rint(0)
            polygon.theT = rint(0)
            polygon.strokeWidth = omero.model.LengthI(10, omero.model.enums.UnitsLength.PIXEL)
          #  points = "318.46875,40.0, 318.0,39.53125, 317.53125,40.0, 318.0,40.39473684210526, 318.46875,40.0"
            polygon.points = rstring(points)
            create_roi(image, [polygon], update_service)

        except Exception as e:
            print(e)
            traceback.print_exc()
        finally:
            conn.close()
            print(f"Disconnected from {host}")


# We have a helper function for creating an ROI and linking it to new shapes
def create_roi(img, shapes, update_service):
    # create an ROI, link it to Image
    roi = omero.model.RoiI()
    # use the omero.model.ImageI that underlies the 'image' wrapper
    roi.setImage(img._obj)
    for shape in shapes:
        roi.addShape(shape)
    # Save the ROI (saves any linked shapes too)
    return update_service.saveAndReturnObject(roi)



# Another helper for generating the color integers for shapes
def rgba_to_int(red, green, blue, alpha=255):
    """ Return the color as an Integer in RGBA encoding """
    r = red << 24
    g = green << 16
    b = blue << 8
    a = alpha
    rgba_int = r+g+b+a
    if (rgba_int > (2**31-1)):       # convert to signed 32-bit int
        rgba_int = rgba_int - 2**32
    return rgba_int


if __name__ == "__main__":
    run_script()
