# User Guide
## User Interface - Client 
<img src="https://user-images.githubusercontent.com/50183122/154842568-0b3bbd9c-1167-4b68-86c9-300b573e173f.png" width="30%" height="30%"><br>
### Server Selection
In this section, you’ll be able to see the names of the servers that are currently are connected to the 
client.<br>
Each operation that you want to do, you must specify on which server you want this operation to 
happen. If you want an operation that will happen on all the available servers, you can select the 
“all” option from the Server Selection combo box. 
If you want to receive the list of files from a specific server, you can click the “Get Files List” button. 
A specific server must be selected for this operation. 
Any server that is specified in the configuration file but is not currently online, will automatically
appear in the server’s combo box upon becoming online.
### File Selection 
In this section, you can specify the name of the file on which you want to apply a specific operation. 
For example: if you want to download a file named “file1.txt” from a specific server, you must write 
“file1.txt” in the File Selection box, and only after this click the Download button. 
### File Options
In this section, you can do different operation on files that are on the server<br>
**Download:** after you chose a server and a file name, clicking this button will let you save the 
specified file on your PC, in the path of your choice. Only one server must be chosen. If such file 
doesn’t exist on the sever, you will be notified in the Status box.<br>
**Lock:** To update an existing file, you must lock if first with this button. This operation will be available
only if “All” is chosen in the Server Selection field and the file name is written in the File Selection
box. This operation is done on all servers. In case of success or failure, you will be notified in the 
Status section.<br>
**Unlock:** To undo the lock operation, you can apply unlock operation on a file with this button. This 
operation will be available only if “All” is chosen in the Server Selection field and the file name is 
written in the File Selection box. This operation is done on all servers. In case of success or failure, 
you will be notified in the Status section.<br>
**Get Version:** To receive the current version of the file, this button must be clicked. The user will 
receive the latest update date of the selected file, and its hash digest. This operation will be applied 
only after selecting a server and the file name is written in the File Selection box. This operation can 
be done on all servers. In case of success or failure, you will be notified in the Status section. 

### Upload Section 
To upload a file to all severs, a file must be chosen from the users PC. To do so, click the Browse
button, after which a file selection dialog will appear. After selecting the chosen file, click OK. 
The name of the chosen file will appear between the Browse and Upload buttons. 
To finally upload the file, simply click on the Upload button. 
This operation will be applied only after selecting a file with the Browse button. This operation will
be done on all servers. In case of success or failure, you will be notified in the Status section.
### Status Section 
There are two purposes for this status box:
1. After clicking “Get Files List” or “Get Version”, the result will be shown in this box. 
2. After each single operation, the user can know if it was successful or not by looking at the 
Status box. For example, if a lock attempt will fail, the text in the status box will be “Can’t 
lock file”. 
### Exit 
To close the client, just click the “X” symbol at the title bar of the window. A specific message will be 
automatically sent to the server.

## User Interface - Server
<img src="https://user-images.githubusercontent.com/50183122/154842802-ee06c9ea-9b5b-47b1-89dd-afb6321bd88a.png" width="40%" height="40%"><br>
After the server’s app will start, the user will be presented with the IPv4s that the server can listen 
on. The user must choose one from the list. Any client that is set to the same IP and the same 
listening port as specified the in the configuration file can connect to the server.
### More Server Options
Meanwhile listening, the user can do the following operations on the server: 
1. Stop Listening: It will stop the listening process, and all previous connection clients will 
disconnect. To apply this option, the user must type “stop”. 
2. Resume listening: It will resume the listening process. To apply this option, the user must 
type “resume”. 
3. Quit: It will close the whole server operation. Can only be applied after the “Stop Listening” 
operation. To apply this option, the user must type “quit”.

## Configuration Files – Server 
### config.txt
The configuration file (config.txt) must be presented in the same folder as the FTPServer.jar file.<br>
The configuration file has two lines: 
1. The path of the storage folder for this specific server 
2. The listening port 
Example of the server’s config file content:
```
C:\Users\user\Desktop\Storage 1\
4000
```
### ServersList.txt
The configuration file – “ServersList.txt” must be presented in the same folder as the FTPServer.jar file.<br>
This file must contain a list of other servers that you want to be connected to any other server that contains this configuration file in its folder.<br>
The configuration file must specify the following info for each server that you want to establish the 
connection with (one server per line): 
1. Server’s IP 
2. Server’s port 
Example of the ServersList.txt config file content:
Example of the server’s config file content:
```
10.0.201.5:3000
10.0.201.6:1000
```
## Configuration File – Client
The configuration file (config.txt) must be presented in the same folder as the FTPClient.jar file.<br>
The configuration file must specify the following info for each server that the client want to connect 
to (one server per line): 
1. Server’s name 
2. Server’s IP 
3. Server’s port 
Example of the client’s config file content:
```
ServerOne 10.0.201.5:3000
ServerTwo 10.0.201.6:1000
ServerThree 10.0.201.7:2000
```
## Log
You can find the log file in the same folder as the servers JAR file. The servers log will document the 
following information: 
1. When the server is up 
2. When the sync operation with other servers has completed
3. When the server starts listening for connection (on which IP and port) 
4. When the server stopped listening 
5. When the server resumed listening 
6. When each client is connected to the server 
7. Each command that is received from the client 
8. Each reply from the server to the client (ex. “OK”, “ERROR”) 
9. Each command that is sent to other servers (ex. “OFFER”)
10. Each reply from other servers (ex. After “DOWNLOAD” or “ERROR” after and “OFFER”)
11. When the client disconnected from the server 
12. Each error and exception that was caught during the servers running process

Each log line is time stamped.
