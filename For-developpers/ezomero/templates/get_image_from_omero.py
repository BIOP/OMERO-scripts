import ezomero
import matplotlib.pyplot as plt
import traceback


def run_script():

    omero_image_ids = [132, 123, 231]
    host = "localhost"
    conn = ezomero.connect(user="username", password="password", group="",
                           host=host, port=4064, secure=True, config_path=None)

    if conn is not None and conn.isConnected():
        try:
            for idx, omero_image_id in enumerate(omero_image_ids):

                img_omero_obj, img_nparray = ezomero.get_image(conn, int(omero_image_id))
                print("The image "+str(img_omero_obj.getId())+" has been imported from OMERO")

                # the image is stored in the order TZYXC
                plt.figure(idx)
                plt.imshow(img_nparray[0][0])
            plt.show()
        except Exception as e:
            print(e)
            traceback.print_exc()
        finally:
            conn.close()
            print(f"Disconnect from {host}")

    else:
        print("ERROR: Not able to connect to OMERO server. Please check your credentials, group and hostname")


if __name__ == "__main__":
    run_script()