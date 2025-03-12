import ezomero
import traceback


def run_script():
    host = "localhost"
    conn = ezomero.connect(user="username", password="password", group="",
                           host=host, port=4064, secure=True, config_path=None)

    if conn is not None and conn.isConnected():
        print(f"Connected to {host}")
        try:
            print("Hello !")

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
