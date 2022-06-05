package bobby;

import java.net.*;
import java.io.*;
import java.util.*;

import java.util.concurrent.Semaphore;

//import jdk.internal.org.jline.utils.InputStreamReader;



public class ServerThread implements Runnable{
	private Board board;
	private int id;
	private boolean registered;
	private BufferedReader input;
	private PrintWriter output;
	private Socket socket;
	private int port;
	private int gamenumber;

	public ServerThread(Board board, int id, Socket socket, int port, int gamenumber){
		
		this.board = board;

		//id from 0 to 4 means detective, -1 means fugitive
		this.id = id;
		
		this.registered = false;

		this.socket = socket;
		this.port = port;
		this.gamenumber = gamenumber;
	}

	public void run(){

		try{

			/*
			PART 0_________________________________
			Set the sockets up
			*/

			//this.socket = new Socket("127.0.0.1",port) ;
			//this.board.dead = false ;
			InputStream in = this.socket.getInputStream();
    		OutputStream out = this.socket.getOutputStream();
			
			//try{
				
				input = new BufferedReader(new InputStreamReader(in)) ;
				output = new PrintWriter(out,true) ;
                                                                  
				if (this.id == -1) {
					output.println(String.format(
							"Welcome. You play Fugitive in Game %d:%d. You start on square 42. Make a move, and wait for feedback",
							this.port, this.gamenumber));
				} else {
					output.println(String.format(
							"Welcome. You play Detective %d in Game %d:%d. You start on square 0. Make a move, and wait for feedback",
							this.id, this.port, this.gamenumber));
				}
			//}
			//catch (IOException i){
				/*
				there's no use keeping this thread, so undo what the
				server did when it decided to run it
				*/
			//	this.socket.close() ;                            
                              
                                    
                                             
			//	return;
			//}

			//__________________________________________________________________________________________

			while(true){
				boolean quit = false;
				boolean client_quit = false;
				boolean quit_while_reading = false;
				int target = -1;

				/*
				client_quit means you closed the socket when you read the input,
				check this flag while making a move on the board

				quit means that the thread decides that it will not play the next round

				if quit_while_reading, you just set the board to dead if 
				you're a fugitive. Don't edit the positions on the board, as threads
				are reading

				quit_while_reading is used when you can't call erasePlayer just yet, 
				because the board is being read by other threads

				INVARIANT: 
				quit == client_quit || quit_while_reading

				client_quit && quit_while_reading == false

				DO NOT edit board between barriers!

				erasePlayer, when called by exiting Fugitive, sets 
				this.board.dead to true. Make sure to only alter it during the
				round

				either when
				1) you're about to play but the client had told you to quit
				2) you've played and the game got over in this round.

				________________________________________________________________________________________
				
				First, base case: Fugitive enters the game for the first time
				installPlayer called by a fugitive sets embryo to false.

				Register the Fugitive, install, and enable the moderator, and 
				continue to the next iteration
				*/
				
				if (this.id == -1 && !this.registered){
					this.board.threadInfoProtector.acquire();
					
					//System.out.println(String.format("SThread %d: Number of registration permits at line 125 : %d",this.id,this.board.registration.availablePermits()));

					this.board.registration.acquire();
					this.registered = true ;
					this.board.installPlayer(this.id);
					
					this.board.threadInfoProtector.release();
					this.board.moderatorEnabler.release();
					continue;
				}

				/*
				Now, usual service
				
				
				PART 1___________________________
				read what the client has to say.
				
				totalThreads and quitThreads are relevant only to the moderator, so we can 
				edit them in the end, just before
				enabling the moderator. (we have the quit flag at our disposal)

				For now, if the player wants to quit,
				just make the id available, by calling erasePlayer.
				this MUST be called by acquiring the threadInfoProtector! 
				
				After that, say goodbye to the client if client_quit
				*/

				String cmd = "";
				try {
					//input = new BufferedReader(new InputStreamReader(System.in)) ;
					//System.out.println(String.format("SThread %d: Taking cmd", this.id));
					cmd = input.readLine() ;
					//System.out.println(String.format("SThread %d: Input cmd : ", this.id)+cmd);
				} 
				catch (IOException i) {
					//set flags
					//this.board.erasePlayer(this.id); 
                    client_quit = true ;    
 					
					// elease everything socket related
					this.socket.close(); 
                    input.close();
					output.close();                    
				}

				if (cmd == null){
					// rage quit (this would happen if buffer is closed due to SIGINT (Ctrl+C) from Client), set flags
					//this.board.erasePlayer(this.id);            
                    client_quit = true ;    

					// release everything socket related
					this.socket.close();            
                    input.close();
					output.close();
                    
				}

				else if (cmd.equals("Q")) {
					// client wants to disconnect, set flags
					//this.board.erasePlayer(this.id);            
                    client_quit = true ;    

					// release everything socket related
					this.socket.close();            
                    input.close();
					output.close();                    
				}

				else{
					try{
						//interpret input as the integer target
						//System.out.println("Target nikalo");
						target = Integer.parseInt(cmd) ;
						//System.out.println("Printing Target: "+target);
					}
					catch(Exception e){
						//set target that does nothing for a mispressed key
						target = -1;
					}
				}

				/*
				In the synchronization here, playingThreads is sacrosanct.
				DO NOT touch it!
				
				Note that the only thread that can write to playingThreads is
				the Moderator, and it doesn't have the permit to run until we 
				are ready to cross the second barrier.
				
				______________________________________________________________________________________
				PART 2______________________
				entering the round

				you must acquire the permit the moderator gave you to enter, 
				regardless of whether you're new.

				Also, if you are new, check if the board is dead. If yes, erase the player, set the
				flags, and drop the connection

				Note that installation of a Fugitive sets embryo to false
				*/
				
				//System.out.println("Number of playing threads : "+this.board.playingThreads);
				//System.out.println("Number of total threads : "+this.board.totalThreads);
				if (!this.registered){

					if(this.board.dead){
						client_quit = true ;
						//this.board.erasePlayer(id);
						this.socket.close() ;
						input.close();
						output.close();
					}
					else if(!this.board.embryo){
						//System.out.println(String.format("SThread %d: Number of registration permits at line 238 : %d",this.id,this.board.registration.availablePermits()));
						this.board.registration.acquire() ;
						this.registered = true ;
						//System.out.println(String.format("SThread %d: Registration permit Acquired",this.id));
						this.board.threadInfoProtector.acquire() ;
						//System.out.println(String.format("SThread %d: ThreadInfoLock Acquired", this.id));
						this.board.installPlayer(this.id) ;
						//System.out.println(String.format("SThread %d: Installed", this.id));
						this.board.threadInfoProtector.release() ;
						//System.out.println(String.format("SThread %d: ThreadInfoLock Released", this.id));
					}                                              
                                                
                    //this.board.registration.release() ;                 
				}

				//System.out.println(String.format("SThread %d: ReEntry semaphores count: %d", this.id,this.board.reentry.availablePermits()));

				this.board.reentry.acquire();
				//System.out.println(String.format("SThread %d: ReEntry permit Acquired",this.id));
				/*
				_______________________________________________________________________________________
				PART 3___________________________________
				play the move you read in PART 1 
				if you haven't decided to quit

				else, erase the player
				*/
				//System.out.println(String.format("SThread %d: Entering PART 3",this.id));
				this.board.threadInfoProtector.acquire();
				//System.out.println(String.format("SThread %d: ThreadInfoLock Acquired",this.id));
				if(!client_quit)
				{
					//System.out.println(String.format("SThread %d: Moving in PART 3",this.id));
					if(id==-1)
					{
						this.board.moveFugitive(target);
					}
					else
					{
						this.board.moveDetective(id, target);
					}
					//System.out.println(String.format("SThread %d: Moved in PART 3, to location : %d",this.id,target));

				}

				else
				{
					//System.out.println(String.format("SThread %d: Erasing in PART 3",this.id));
					this.board.erasePlayer(id);
					//System.out.println(String.format("SThread %d: Erased player",this.id));
				}
				this.board.threadInfoProtector.release();
				//System.out.println(String.format("SThread %d: ThreadInfoLock Released",this.id));
              
				/*

				_______________________________________________________________________________________

				PART 4_____________________________________________
				cyclic barrier, first part
				
				execute barrier, so that we wait for all playing threads to play

				Hint: use the count to keep track of how many threads hit this barrier
				they must acquire a permit to cross. The last thread to hit the barrier can 
				release permits for them all.
				*/
				//System.out.println(String.format("SThread %d: First barrier reached",this.id));
				//System.out.println(this.board.countProtector.availablePermits()) ;
				this.board.countProtector.acquire();
				//System.out.println(String.format("SThread %d: CountProtecc Acquired",this.id));
				this.board.count++;
				
				if(this.board.count==this.board.playingThreads)
				{
					this.board.barrier1.release(this.board.count);
					this.board.count = 0 ;
					//System.out.println(String.format("SThread %d: %d Barrier1 semaphores released",this.id,this.board.count));
				}
				this.board.countProtector.release();
                //System.out.println(String.format("SThread %d: CountProtecc Released",this.id));
				this.board.barrier1.acquire();
				//this.board.barrier1.release();
                          
				//System.out.println(String.format("SThread %d: First Barrier Cleared",this.id));
                                                            
				/*
				________________________________________________________________________________________

				PART 5_______________________________________________
				get the State of the game, and process accordingly. 

				recall that you can only do this if you're not walking away, you took that
				decision in PARTS 1 and 2

				It is here that everyone can detect if the game is over in this round, and decide to quit
				*/

				if (!client_quit){
					String feedback;
					                                         
                    if(this.id == -1)
					{
						feedback = this.board.showFugitive() ;
					}    

					else
					{
						feedback = this.board.showDetective(id) ;
					}     
					
					String[] strFeedback = feedback.split("; ",0) ;
                                              

					//pass this to the client via the socket output
					try{
						output.println(feedback);
						//System.out.println(String.format("SThread %d: Printed feedback", this.id));
					}
					//in case of IO Exception, off with the thread
					catch(Exception i){
						//set flags 
						quit_while_reading=true;                          
                  
						// If you are a Fugitive you can't edit the board, but you can set dead to true
						if(this.id == -1){
							
							this.board.dead=true;	                                         
                              
                        }

						// release everything socket related
						socket.close();             
                     
                     
					}

					
					
					//parse this feedback to find if game is on
					String indicator;
					indicator = strFeedback[2];

					//System.out.println(String.format("SThread %d: Indicator: ",this.id)+indicator);
					if (!indicator.equals("Play")){
						//Proceed simillarly to IOException
						output.println(indicator) ;
						quit_while_reading = true ;
						this.board.dead = true ;                    
					}
				}

				/*
				__________________________________________________________________________________
				PART 6A____________________________
				wrapping up


				everything that could make a thread quit has happened
				now, look at the quit flag, and, if true, make changes in
				totalThreads and quitThreads
				*/
				//System.out.println(String.format("SThread %d: Reached PART 6A", this.id));
				quit = quit_while_reading || client_quit ;
				
				this.board.threadInfoProtector.acquire();
				//System.out.println(String.format("SThread %d: ThreadInfoLock Acquired",this.id));

				if(quit)
				{	
					//this.board.erasePlayer(this.id); //can be called multiple times for same id
					this.board.quitThreads++ ;
					this.board.totalThreads-- ;
				}                             
                              
				this.board.threadInfoProtector.release();
				//System.out.println(String.format("SThread %d: ThreadInfoLock Released",this.id));

				/*
				__________________________________________________________________________________
				PART 6B______________________________
				second part of the cyclic barrier
				that makes it reusable
				
				our threads must wait together before proceeding to the next round

				Reuse count to keep track of how many threads hit this barrier2 

				The code is similar. 
				*/
				
				this.board.countProtector.acquire();
				this.board.count++ ;
				//System.out.println(String.format("SThread %d: 6B Number of playing threads: %d", this.id,this.board.playingThreads));
				if(this.board.count==this.board.playingThreads)
				{
					
					this.board.barrier2.release(this.board.count);
					//System.out.println(String.format("SThread %d: %d Barrier2 Released",this.id,this.board.count));
					this.board.count = 0 ;
					//this.board.threadInfoProtector.acquire();
					//this.board.totalThreads = this.board.totalThreads - this.board.quitThreads ;
					//this.board.threadInfoProtector.release();
					
					this.board.moderatorEnabler.release() ;
					//System.out.println(String.format("SThread %d: ModEnabler Released",this.id));
				}
				this.board.countProtector.release();

				this.board.barrier2.acquire();
				//System.out.println(String.format("SThread %d: Barrier2 Acquired",this.id));

				/*
				__________________________________________________________________________________
				PART 6C_________________________________
				actually finishing off a thread
				that decided to quit

				However, the last thread to hit this must issue one
				permit for the moderator to run

				If all else fails use the barriers again
				*/
				
				
				//Moderator enabling has been coupled with barrier2, when the last thread hits barier2, moderatorEnabler will be released

				if(quit) //PART 6C
				{
					this.board.threadInfoProtector.acquire();
					this.board.erasePlayer(this.id); //can be called multiple times for same id
					this.board.threadInfoProtector.release();
				//	System.out.println(String.format("SThread %d: Exiting while loop", this.id)) ;
					break ;
				}
				//System.out.println(String.format("SThread %d: Looping", this.id));
			}
		}
		catch (InterruptedException ex) {
			return;
		}
		catch (IOException i){
			return;
		}
	}

	
}
