package bobby;

import java.net.*;
import java.io.*;
import java.util.*;

import java.util.concurrent.Semaphore;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class ScotlandYard implements Runnable{

	/*
		this is a wrapper class for the game.
		It just loops, and runs game after game
	*/

	public int port;
	public int gamenumber;

	public ScotlandYard(int port){
		this.port = port;
		this.gamenumber = 0;
	}

	public void run(){
		while (true){
			Thread tau = new Thread(new ScotlandYardGame(this.port, this.gamenumber));
			tau.start();
			try{
				tau.join();
			}
			catch (InterruptedException e){
				return;
			}
			this.gamenumber++;
		}
	}

	public class ScotlandYardGame implements Runnable{
		private Board board;
		private ServerSocket server;
		public int port;
		public int gamenumber;
		private ExecutorService threadPool;

		public ScotlandYardGame(int port, int gamenumber){
			this.port = port;
			this.board = new Board();
			this.gamenumber = gamenumber;
			this.server = null ;
			System.out.println(String.format("About to create a serversocket with port: %d",this.port));
			while(this.server == null)
			{
				try{
					this.server = new ServerSocket(port);
					System.out.println("Created a serversocket");
					System.out.println(String.format("Game %d:%d on", port, gamenumber));
					server.setSoTimeout(5000);
				}
				catch (IOException i) {
					System.out.println("IOException");
					continue;
				}
			}
			this.threadPool = Executors.newFixedThreadPool(10);
			System.out.println("Made threadpool");
		}


		public void run(){

			try{
			
				//INITIALISATION: get the game going

				//this.board.dead = false ;

				Socket socket = null;
				boolean fugitiveIn;
				
				/*
				listen for a client to play fugitive, and spawn the moderator.
				
				here, it is actually ok to edit this.board.dead, because the game hasn't begun
				*/
				fugitiveIn = false ;
				
				do{
			                    
					try {
						//this.board.dead = false ;
						socket = this.server.accept() ;
					}
					catch (SocketTimeoutException t){
                                               
                        if(!this.board.dead)    
						{
							this.board.dead = true ;
							continue;
						}
						else if(this.board.embryo)
						{
							continue ;
						}
					}

					catch(NullPointerException npe){
						if(this.board.dead)
						{
							//threadPool.shutdown() ;    
							          
							System.out.println(String.format("Game %d:%d Over", this.port, this.gamenumber));
							return;
						}
					}
					//BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream())) ;
					//PrintWriter output = new PrintWriter(socket.getOutputStream(),true) ;
                    //int move;
					//move = Integer.parseInt(input.readLine()) ;
				
                    //if(strLine[3].equals("Fugitive"))
					fugitiveIn = true ;
					//Moderator moderator = new Moderator(this.board) ;
					this.board.dead = false ;
					//else
					//{
				//		socket.close();
			//			input.close();
		//				output.close();
	//				}
      
				} while (!fugitiveIn);
				
				System.out.println(this.gamenumber);

				// Spawn a thread to run the Fugitive
                                             
                try{
					threadPool.execute(new ServerThread(this.board, -1, socket, this.port, this.gamenumber)) ;                           
				//	System.out.println("Server: Creating new Fuge");
				}
				catch(NullPointerException npe){
					if(this.board.dead)
					{
						threadPool.shutdown() ;
						//mod.join() ;
						this.server.close();                    
					//	System.out.println(String.format("Game %d:%d Over", this.port, this.gamenumber));
						return;
					}
				}
				this.board.threadInfoProtector.acquire();
				this.board.totalThreads = 1 ;                                                                                 
                this.board.threadInfoProtector.release();

				// Spawn the moderator
				//System.out.println("Server: Creating new mod");
                Thread mod = new Thread(new Moderator(this.board)) ; 
				mod.start() ;
				//System.out.println("Mod ke paar");
                
				while(true){
					/*
					listen on the server, accept connections
					if there is a timeout, check that the game is still going on, and then listen again!
					*/
					Socket sock = null ;
					try {
						sock = this.server.accept() ; 
					} 
					catch (SocketTimeoutException t){
                                               
                        if(!this.board.dead)    
						{
						continue;
						}
					}
					catch(NullPointerException npe){
						if(this.board.dead)
						{
							threadPool.shutdown() ;  
							mod.join() ;
							this.server.close();               
							System.out.println(String.format("Game %d:%d Over", this.port, this.gamenumber));
							return;
						}
					}
					
					
					/*
					acquire thread info lock, and decide whether you can serve the connection at this moment,

					if you can't, drop connection (game full, game dead), continue, or break.

					if you can, spawn a thread, assign an ID, increment the totalThreads

					don't forget to release lock when done!
					*/
					this.board.threadInfoProtector.release() ;
					//System.out.println("Socket maan gaya shayad") ;                                       
                    this.board.threadInfoProtector.acquire();      
                    //System.out.println("Server: ThreadInfoLock Acquired");
					//game full
					int availableID = this.board.getAvailableID() ;
					//System.out.println("Available ID : "+availableID);
					if(availableID==-1)
					{
						try{
							sock.close();
						}
						catch(NullPointerException npe){
							if(this.board.dead)
							{
								threadPool.shutdown() ;  
								mod.join() ;
								this.server.close();             
								System.out.println(String.format("Game %d:%d Over", this.port, this.gamenumber));
								return;
							}
						}
						this.board.threadInfoProtector.release();
					//	System.out.println("Server: ThreadInfoLock Released");
						continue ;
					}
					
					if(this.board.dead)
					{
						try{
							sock.close();
						}
						catch(NullPointerException npe){
							if(this.board.dead)
							{
								threadPool.shutdown() ;  
								mod.join() ;
								this.server.close();    
								System.out.println(String.format("Game %d:%d Over", this.port, this.gamenumber));
								return;
							}
						}
						this.board.threadInfoProtector.release();
					//	System.out.println("Server: ThreadInfoLock Released");
						break ;
					}
                   // System.out.println("Bas detective banane wala hu") ;                      
                    try{
				  	 	threadPool.execute(new ServerThread(this.board, availableID, sock, port, gamenumber)) ;
					}
					catch(NullPointerException npe){
						if(this.board.dead)
						{
							threadPool.shutdown() ;  
							mod.join() ;
							this.server.close();            
							System.out.println(String.format("Game %d:%d Over", this.port, this.gamenumber));
							return;
						}
					}
						   // System.out.println("Ab toh detective bana diya");
					this.board.totalThreads++ ; 
                    this.board.threadInfoProtector.release();  
				//	System.out.println("Server: ThreadInfoLock Released");                               

				}

				/*
				reap the moderator thread, close the server, 
				
				kill threadPool (Careless Whispers BGM stops)
				*/
				
			    threadPool.shutdown() ;              
                mod.join() ;              
    
				System.out.println(String.format("Game %d:%d Over", this.port, this.gamenumber));
				this.server.close();
				System.out.println("ServerSocket closed");
				return;
			}
			catch (InterruptedException ex){
				System.err.println("An InterruptedException was caught: " + ex.getMessage());
				ex.printStackTrace();
				return;
			}
			catch (IOException i){
				return;
			}
			
		}

		
	}

	public static void main(String[] args) {
		for (int i=0; i<args.length; i++){
			int port = Integer.parseInt(args[i]);
			Thread tau = new Thread(new ScotlandYard(port));
			tau.start();
		}
	}
}
