## How client identity is used in global locking and unlocking 
To identify clients, each server has a client’s indexer which is used to store the clients RemoteSocketAddress and the clients ID. When the client app is up, it creates a random 10-letter string, that is used as clients ID, upon successful connection to a server, the client sends this ID to the new server, so that the server could store it in its client’s indexer. 
<br><br>
For example, first client is up, and it generates a random string as its own ID. He sends this string to the online server, and the server stores it as following: <br>
| RemoteSocketAddress  | ID |
| ------------- | ------------- |
| 10.0.201.4:54687   | hfdurnvkdue  |
<br>

Now let’s say another server is up. The existing client automatically sends its own ID to it upon connection, so that the second server has the following client’s indexer:
| RemoteSocketAddress  | ID |
| ------------- | ------------- |
| 10.0.201.4:56941   | hfdurnvkdue  |

As you can see, the two servers acknowledge the client by a <b>different RemoteSocketAddress value</b>, but they know it’s the same client because the client’s indexer have the <b>same ID value</b> of the different RemoteSocketAddress values.
<br>
In case of lock request by a client (ex. “LOCK file1.pdf”), the master server which received this request, looks up the clients ID in its client’s indexer object, trying to lock the file with the clients ID as identifier. If the lock operation was successful on the master server, it passes the lock request to other servers, but this time with the client’s ID as another parameter, ex. “LOCK file1.pdf hfdurnvkdue”. This way the other servers can store the lock information as from the same client, since they should have this ID in their client’s indexer – each server holds the same client ID for each client. The unlock process work the same way.
<br><br>
Here’s a graph that explains the lock process:<br>
<img src="https://user-images.githubusercontent.com/50183122/156371152-a38861a5-4976-4edf-aa77-e66be61f5d10.png" width="70%" height="70%"><br>

