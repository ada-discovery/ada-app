# Ada (NCER-PD Reporting System)

## Application Server (Apache Tomcat)

**Start**
```bash
ssh -p 8022 yourusername@10.79.2.192
cd /home/peter.banda/apache-tomcat-7.0.64/bin
source set_env.sh
./startup.sh
```

**Stop**
```bash
ssh -p 8022 yourusername@10.79.2.192
cd /home/peter.banda/apache-tomcat-7.0.64/bin
./shutdown.sh
ps -A | grep java (to check if still running, if yes do: 'kill -s kill pid')
```

**Config**
```bash
/home/peter.banda/apache-tomcat-7.0.64/bin/catalina.sh
````

**Log**
```bash
/home/peter.banda/apache-tomcat-7.0.64/logs/catalina.out
```

**Backup script**
```bash
/etc/cron.daily/ada-backup
```

## Database (Mongo)

**Start**
```bash
ssh -p 8022 yourusername@10.79.2.71
sudo service mongod start
```

**Stop**
```bash
ssh -p 8022 yourusername@10.79.2.71
sudo service mongod stop
```

**Config**
```bash
/etc/mongod.conf
```

**Log**
```bash
/var/log/mongodb/mongod.log
```

**Backup script**
```bash
/etc/cron.daily/ada-db-backup
```