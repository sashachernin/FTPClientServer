## Thread Safety
The main tools that I used to implement thread safety is ConcurrentHashMap on the server side, and 
a synchronized function on the client side. <br>
The main method that helped me on the server side, is the atomic putIfAbsent method of the 
ConcurrentHashMap. <br>
One of the calls will always occur first, and the second will wait for it to complete. This is part of a 
ConcurrentMap's thread safety.<br>

### These are the cases where I needed to implement some thread safety mechanism
#### More than one thread is trying to upload a file with the same name at the same time.
To solve the problem here, two ConcurrentHashMap were implemented, one for the file indexer, 
and the other as a list of locked files. <br>
**File indexer map** contains files name as the key, and the hash digest info as the value. <br>
**Locked files map** contains files name as the key, and the client info which locked the file as the value. 

Here’s the algorithm graph that details the thread safety mechanism that is applied when a user is 
trying to upload a file:<br>
![image](https://user-images.githubusercontent.com/50183122/154843470-9bc41a9f-ab04-4a3e-a97f-bce368ab3919.png)<br>
*Note:* The OFFER message that is sent by the master server to other servers is excluded from the graph, 
because it is not even sent to other servers if the upload is failed on the master server.

#### More that on thread is trying to lock the same file
![image](https://user-images.githubusercontent.com/50183122/154843538-6f99aa64-11f9-41df-8a57-e58274c41908.png)<br>
*Notes:*
1.	The Lock message that is sent by the master server to other servers is excluded from the graph, because it is not even sent to other servers if the lock operations is failed on the master server.
2.	After the file is unlocked, it is removed from the locked files indexer

#### More than one thread is trying to update the status box in the client 
Since the communication between the client and server on the client side is each own thread, such 
each thread might try to update the Status box at the same time if the client is doing an operation 
on all servers. 
For such case, I created on the client side a synchronized function that have access to the Status box 
text. So that when two threads trying to update the Status box, they are doing this thru a 
synchronized function. 
