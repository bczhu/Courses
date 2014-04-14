src="src"
bin="bin"
all:
	javac $(src)/Protocol.java -d $(bin) -cp $(bin)
	javac $(src)/Logging.java -d $(bin) -cp $(bin)
	javac $(src)/Util.java -d $(bin) -cp $(bin)
	javac $(src)/Server.java -d $(bin) -cp $(bin)
	javac $(src)/Leader.java -d $(bin) -cp $(bin)
	javac $(src)/Acceptor.java -d $(bin) -cp $(bin)
	javac $(src)/Server.java -d $(bin) -cp $(bin)
	javac $(src)/Client.java -d $(bin) -cp $(bin)
	javac $(src)/Master.java -d $(bin) -cp $(bin) 

clean:
	rm -rf bin/*.class
