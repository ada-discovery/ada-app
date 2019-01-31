#!/bin/bash

echo "    ______         __           
   /      \       |  \          
  |  ######\  ____| ##  ______  
  | ##__| ## /      ## |      \ 
  | ##    ##|  #######  \######\\
  | ########| ##  | ## /      ##
  | ##  | ##| ##__| ##|  #######
  | ##  | ## \##    ## \##    ##
   \##   \##  \#######  \#######
---------------------------------
      MIGRATION ASSISTANT
---------------------------------
                                  "
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

CUR_DIR=$SCRIPT_DIR/../
APP_CONF_FILE=$CUR_DIR/conf/application.conf

if [ ! -f $APP_CONF_FILE ];
then
    echo -e "\n>>> Something is wrong. This script was not run from an Ada installation dir.\n"	
    exit 1
fi

APP_VERSION_LINE=$(grep -F "app.version" $APP_CONF_FILE)
APP_VERSION=${APP_VERSION_LINE#*= }

echo -e "Current Ada version is $APP_VERSION.\n"

PREV_DIR_CORRECT=""
while [[ $PREV_DIR_CORRECT != "y" && $PREV_DIR_CORRECT != "Y" ]];
do
  # PREV_DIR="USER INPUT"
  read -p "Enter the (root) dir of your previous Ada installation: " PREV_DIR

  if [ -d $PREV_DIR ];
  then
    PREV_APP_CONF_FILE=$PREV_DIR/conf/application.conf
    if [ -f $PREV_APP_CONF_FILE ];
    then
      PREV_APP_VERSION_LINE=$(grep -F "app.version" $PREV_APP_CONF_FILE)
      PREV_APP_VERSION=${PREV_APP_VERSION_LINE#*= }
      echo -e "\n>>> Found the app version $PREV_APP_VERSION in the dir '$PREV_DIR'.\n"	
      # PREV_DIR_CORRECT="USER INPUT"
      read -p "Is it correct [y/n]?" PREV_DIR_CORRECT
    else
      echo -e "\n>>> Cannot find an application config. The dir '$PREV_DIR' does not seem to belong to an Ada instalation.\n"
    fi
  else
    echo -e "\n>>> The dir '$PREV_DIR' does not exist.\n"
  fi
done

# Configuration files

# cp $PREV_DIR/conf/custom.conf $CUR_DIR/conf/custom.conf
# cp $PREV_DIR/bin/set_env.sh $CUR_DIR/bin/set_env.sh
# cp $PREV_DIR/bin/runme $CUR_DIR/bin/runme
# cp $PREV_DIR/bin/stopme $CUR_DIR/bin/stopme

# Copy extra folders, e.g. dataImports, and images

cd $PREV_DIR

echo -e "\n>>> Searching for extra sub dirs to copy.\n"

for i in $(ls -d */);
do 
  SUB_DIR=${i%%/};
  if [[ $SUB_DIR != "bin" && $SUB_DIR != "conf" && $SUB_DIR != "lib" && $SUB_DIR != "share" ]];
  then
    echo "Copying '$SUB_DIR'..."
    cp -r $PREV_DIR/$SUB_DIR $CUR_DIR/$SUB_DIR
  fi
done

# Mongo
# >   2.  Execute (Mongo) db migration script from the db-update file using your favorite Mongo client (e.g. Robo3T)
