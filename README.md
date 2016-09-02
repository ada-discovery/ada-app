Ada (NCER-PD Reporting System)

Application (Server)

Start

ssh -p 8022 yourusername@10.79.2.192
cd /home/peter.banda/apache-tomcat-7.0.64/bin
./startup.sh

Stop

ssh -p 8022 yourusername@10.79.2.192
cd /home/peter.banda/apache-tomcat-7.0.64/bin
./shutdown.sh
ps -A | grep java (to check if it still running, if yes do: 'kill -s kill pid')

Backup script

/etc/cron.daily/ada-backup

DB (Mongo)

Start

ssh -p 8022 yourusername@10.79.2.71
sudo service mongod start

Stop

ssh -p 8022 yourusername@10.79.2.71
sudo service mongod stop

Backup script

/etc/cron.daily/ada-db-backup