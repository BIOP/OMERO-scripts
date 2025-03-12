import ezomero
import os
import traceback


def run_script():
        img_path = "Path/to/the/image.tif"
        dataset_id = 132

        host = "localhost"
        conn = ezomero.connect(user="username", password="password", group="",
                               host=host, port=4064, secure=True, config_path=None)

        if conn is not None and conn.isConnected():
            try:
                os.chdir(os.path.dirname(img_path))
                img_ids = ezomero.ezimport(conn, os.path.basename(img_path), dataset=dataset_id)
                print("The image has been uploaded on OMERO with id " + str(','.join(str(e) for e in img_ids)) +
                      " in dataset " + str(dataset_id))
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