import pyminizip

def run_script():
    # # compress only one file
    # pyminizip.compress("dummy.txt", "test", "myzip2.zip", "12345", 1)

    # compress multiple files in the same zip
    pyminizip.compress_multiple(['dummy1.py', 'dummy2.py'], [], "D:/user/test/myzip3.zip", "12345", 1)

    print("done")

if __name__ == "__main__":
    run_script()
