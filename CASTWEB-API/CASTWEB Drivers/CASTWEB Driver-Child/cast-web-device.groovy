/**
 *  cast-web-device
 *
 *  Copyright 2017 Tobias Haerke
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * Version Control:
 * 1.2.1  2021-04-29 logname       Port for Hubitat Elevation
 */
import org.json.JSONObject

preferences {
    input("configOn", "enum", title: "Switch on does?",
        required: false, multiple:false, value: "nothing", options: ["Play","Pause","Stop","Play preset 1","Play preset 2","Play preset 3","Play preset 4","Play preset 5","Play preset 6"])
    input("configOff", "enum", title: "Switch off does?",
        required: false, multiple:false, value: "nothing", options: ["Play","Pause","Stop","Play preset 1","Play preset 2","Play preset 3","Play preset 4","Play preset 5","Play preset 6"])
    input("configNext", "enum", title: "Next song does?",
        required: false, multiple:false, value: "nothing", options: ["Play","Pause","Stop","Next preset","Previous preset","Play preset 1","Play preset 2","Play preset 3","Play preset 4","Play preset 5","Play preset 6"])
    input("configPrev", "enum", title: "Previous song does?",
        required: false, multiple:false, value: "nothing", options: ["Play","Pause","Stop","Next preset","Previous preset","Play preset 1","Play preset 2","Play preset 3","Play preset 4","Play preset 5","Play preset 6"])
    input("configResume", "enum", title: "Resume/restore (if nothing was playing before) plays preset?",
        required: false, multiple:false, value: "nothing", options: ["1","2","3","4","5","6"])
    input("configLoglevel", "enum", title: "Log level?",
        required: false, multiple:false, value: "0", options: ["0","1","2","3","4"])
    input("halt", "bool", title: "Stop after TTS?", required: false)
    input("googleTTS", "bool", title: "Use Google's TTS voice?", required: false)
    input("googleTTSLanguage", "enum", title: "Google TTS language?",
        required: false, multiple:false, value: "nothing", options: ["cs-CZ","da-DK","de-DE","en-AU","en-CA","en-GH","en-GB","en-IN","en-IE","en-KE","en-NZ","en-NG","en-PH","en-ZA","en-TZ","en-US","es-AR","es-BO","es-CL","es-CO","es-CR","es-EC","es-SV","es-ES","es-US","es-GT","es-HN","es-MX","es-PA","es-PY","es-PE","es-PR","es-DO","es-UY","es-VE","eu-ES","fr-CA","fr-FR","it-IT","lt-LT","hu-HU","nl-NL","nb-NO","pl-P","pt-BR","pt-PT","ro-RO","sk-SK","sl-SI","fi-FI","sv-SE","ta-IN","vi-VN","tr-TR","el-GR","bg-BG","ru-RU","sr-RS","he-IL","ar-AE","fa-IR","hi-IN","th-TH","ko-KR","cmn-Hant-TW","yue-Hant-HK","ja-JP","cmn-Hans-HK","cmn-Hans-CN"])
}

metadata {
    definition (name: "cast-web-device", namespace: "vervallsweg", author: "Tobias Haerke") {
        capability "Actuator"
        capability "Audio Notification"
        capability "Music Player"
        capability "Polling"
        capability "Refresh"
        capability "Speech Synthesis"
        capability "Switch"
		capability "Notification"
        capability "Switch Level"
        //capability "Health Check" //TODO: Implement health check

        command "checkForUpdate"
    }

    simulator {
        // TODO: define status and reply messages here
    }
}

// Device handler states
def installed() {
    logger('debug', "Executing 'installed'")
    log.debug "installed"

    //Preset, update-status tiles
    refresh()
    sendEvent(name: "updateStatus", value: ("Version "+getThisVersion() + "\nClick to check for updates"), displayed: false)
    parsePresets()
    refresh() //If callback exists already
}

def updated() {
    logger('debug', "Executing 'updated'")
    log.debug "updated"

    //Preset, update-status tiles
    refresh()
    sendEvent(name: "updateStatus", value: ("Version "+getThisVersion() + "\nClick to check for updates"), displayed: false)
    parsePresets()
}

def refresh() {
    apiCall('/', true)
}

// parse events into attributes
def parse(String description) {
    try {
        logger('debug', "'parse', parsing: '${description}'")
        def msg = parseLanMessage(description)
        logger('debug', 'parse, msg.json: ' + msg.json)

        if(msg.json!=null){
            if(!msg.json.response) {
                if(msg.json.status) {
                    setTrackData(msg.json.status)
                    generateTrackDescription()
                }
                if(msg.json.connection) {
                    logger('debug', "msg.json.connection: "+msg.json.connection)
                    sendEvent(name: "connection", value: msg.json.connection, displayed:false)
                }
            } else if( !msg.json.response.equals('ok') ) {
                logger('error', "json response not ok: " + msg.json)
            }
        }
    } catch (e) {
        logger('error', "Exception caught while parsing data: "+e) //TODO: Health check
    }
}

// handle commands
def play() {
    logger('debug', "Executing 'play'")
    apiCall('/play', true);
}

def pause() {
    logger('debug', "Executing 'pause'")
    apiCall('/pause', true);
}

def stop() {
    logger('debug', "Executing 'stop'")
    apiCall('/stop', true);
}

def nextTrack() {
    logger('debug', "Executing 'nextTrack' encode: ")
    selectableAction(settings.configNext)
}

def previousTrack() {
    logger('debug', "Executing 'previousTrack'")
    selectableAction(settings.configPrev)
}

def setLevel(level,duration=null) {
    logger('debug', "Executing 'setLevel', level: " + level)
    double lvl
    try { lvl = (double) level; } catch (e) {
        lvl = Double.parseDouble(level)
    }
    if( lvl == device.currentValue("level") ){
        logger('debug', "Setting group level: " + level)
        apiCall('/volume/'+lvl+'/group', true)
        return
    }
    apiCall('/volume/'+lvl, true)
	runIn(2, refresh)
}

def mute() {
    logger('debug', "Executing 'mute'")
    apiCall('/muted/true', true)
}

def unmute() {
    logger('debug', "Executing 'unmute'")
    apiCall('/muted/false', true)
}

def setTrack(trackToSet) {
    logger('debug', "Executing 'setTrack'")
    return playTrack(trackToSet)
}

def resumeTrack(trackToSet) {
    logger('debug', "Executing 'resumeTrack'")
    return playTrack(trackToSet)
}

def restoreTrack(trackToSet) {
    logger('debug', "Executing 'restoreTrack'")
    return playTrack(trackToSet)
}

def on() {
    logger('debug', "Executing 'on'")
    selectableAction(settings.configOn)
}

def off() {
    logger('debug', "Executing 'off'")
    selectableAction(settings.configOff ?: 'Stop')
}


def speak(phrase, resume = false) {
    if(settings.googleTTS && settings.googleTTSLanguage){
        if(settings.googleTTS==true) {
            return playTrack( phrase, 0, 0, true, settings.googleTTSLanguage )
        }
    }
    //return playTrack( textToSpeech(phrase, true).uri, 0, 0, true )
	return playTrack( textToSpeech(phrase.replaceAll("%20"," ")).uri, 0, 0, true )
}
//AUDIO NOTIFICATION, TEXT
def playText(message, level, resume = false) {
    logger('info', "playText, message: " + message + " level: " + level)

    if (level!=0&&level!=null) { setLevel(level) }
    return speak(message, true)
}

def playTextAndResume(message, level = 0, thirdValue = 0) {
    logger('info', "playTextAndResume, message: " + message + " level: " + level)
    playText(message, level, true)
}

def playTextAndRestore(phrase, volume) {
    logger('info', "playTextAndRestore, message: " + message + " level: " + level)
    def oldlevel = level
	setLevel(volume)
	//TODO: Reset level to level before the message was played
	runIn (10,setLevel (oldlevel))
	return playTrack( textToSpeech(phrase.replaceAll("%20"," ")).uri, 0, 0, true )

}

def playTrackAtVolume(trackToPlay, level = 0) {
    logger('info', "playTrackAtVolume" + trackToPlay)

    def url = "" + trackToPlay;
    return playTrack(url, level)
}
//AUDIO NOTIFICATION, TRACK
def playTrack(uri, level = 0, thirdValue = 0, resume = false, googleTTS = false) {
	if (halt){
		runIn(20,stop)
	}
	logger('info', "Executing 'playTrack', uri: " + uri + " level: " + level + " resume: " + resume)

    if (level!=0&&level!=null) { setLevel(level) }

    def data = '{ "mediaType":"audio/mp3", "mediaUrl":"'+uri+'", "mediaStreamType":"BUFFERED", "mediaTitle":"Hubitat", "mediaSubtitle":"Hubitat playback", "mediaImageUrl":"https://raw.githubusercontent.com/ryancasler/Hubitat_Ryan/master/Icons/Icon4.png"}'
    if(googleTTS) {
        data = '{ "mediaType":"audio/mp3", "mediaUrl":"", "mediaStreamType":"BUFFERED", "mediaTitle":"'+uri+'", "mediaSubtitle":"Hubitat notification", "mediaImageUrl":"https://raw.githubusercontent.com/ryancasler/Hubitat_Ryan/master/Icons/Icon4.png", "googleTTS":"'+googleTTS+'"}'
    }
    if(resume) {
        def number = 0
        JSONObject preset = null
        if(settings.configResume) { number = settings.configResume }
        if(getTrackData(['preset'])[0]) { number = getTrackData(['preset'])[0] }
        log.warn 'number: '+number
        if(number > 0) {
            preset = getPresetObject(number)
        }

        if (preset) {
            preset.each {
                data = data + ', '+it.toString()
            }
            //data = data + ', '+preset.toString()
        }
    }
    data = "["+data+"]"
    log.warn 'playTrack() data: '+data
    return setMediaPlaybacks(data)
}

def playTrackAndResume(uri, level = 0) {
    logger('info', "Executing 'playTrackAndResume', uri: " + uri + " level: " + level)
    return playTrack(uri, level, 0, true)
}


def playTrackAndRestore(uri, level = 0) {
    logger('info', "Executing 'playTrackAndRestore', uri: " + uri + " level: " + level)
    //TODO: Reset level to level before the track was played
    return playTrack(uri, level, 0, true)
}

def playTrackAndRestore(String uri, String duration, level = 0) {
    logger('info', "Executing 'playTrackAndRestore', uri: " + uri + " duration: " + duration + " level: " + level)
    //TODO: Reset level to level before the track was played
    return playTrack(uri, level, 0, true)
}

def generateTrackDescription() {
    def trackDescription = getTrackData(["title"])[0] + "\n" + getTrackData(["application"])[0] + "\n" + removePresetMediaSubtitle(getTrackData(["subtitle"])[0])

    logger('debug', "Executing 'generateTrackDescription', trackDescription: "+ trackDescription)
    sendEvent(name: "trackDescription", value: trackDescription, displayed:false)
}

def setTrackData(newTrackData) {
    JSONObject currentTrackData = new JSONObject( device.currentValue("trackData") ?: "{}" )
    logger('debug', "setTrackData() currentTrackData: "+currentTrackData+", newTrackData: "+newTrackData)
    def changed = false

    newTrackData.each { key, value ->
        if(key=='connection'||key=='volume'||key=='muted'||key=='application'||key=='status'||key=='title'||key=='subtitle'||key=='image'||key=='preset'||key=='groupPlayback') {
            if(currentTrackData.has(key)) {
                if(currentTrackData.get(key)==value) { return }
            }
            currentTrackData.put(key, value); changed=true;
            if(currentTrackData.has('volume')) {
                sendEvent(name: "level", value: currentTrackData.get('volume'), unit: "%", changed: true)
            }
            if(currentTrackData.has('muted')) {
                if(currentTrackData.get('muted')) {
                    sendEvent(name: "mute", value: "muted", changed: true)
                } else {
                    sendEvent(name: "mute", value: "unmuted", changed: true)
                }
            }
            if(currentTrackData.has('status')) {
                if( currentTrackData.get('status').equals("PLAYING") || currentTrackData.get('status').equals("PAUSED") ) {
                    if( currentTrackData.has('groupPlayback') ) {
                        if( currentTrackData.get('groupPlayback') ) {
                            sendEvent(name: "status", value: 'group', changed: true)
                            sendEvent(name: "switch", value: on, displayed: false)
                        } else {
                            sendEvent(name: "status", value: currentTrackData.get('status').toLowerCase(), changed: true)
                            sendEvent(name: "switch", value: on, displayed: false)
                        }
                    } else {
                        sendEvent(name: "status", value: currentTrackData.get('status').toLowerCase(), changed: true)
                        sendEvent(name: "switch", value: on, displayed: false)
                    }
                } else if( currentTrackData.get('application').equals("") || currentTrackData.get('application').equals("Backdrop") ) {
                    sendEvent(name: "status", value: "ready", changed: true)
                    sendEvent(name: "switch", value: off, displayed: false)
                }
            }
            if(currentTrackData.has('preset')) {
                logger( 'debug', "setTrackData() sendEvent presetName playing for: "+ currentTrackData.get('preset') )
                sendEvent(name: "preset"+currentTrackData.get('preset')+"Name", value: "Playing", displayed: false, changed: true)
                parsePresets( currentTrackData.get('preset') )
            }
        }
    }

    if(changed){
        logger('debug', "sendEvent trackdata, currentTrackData: "+currentTrackData)
        sendEvent(name: "trackData", value: currentTrackData, displayed:false)
    }
}

def getTrackData(keys) {
    def returnValues = []
    logger('debug', "getTrackData, keys: "+keys)
    JSONObject trackData = new JSONObject( device.currentValue("trackData") ?: "{}" )

    keys.each {
        def defaultValue = null
        if( it.equals('title') || it.equals('subtitle') ) { defaultValue="--" }
        if( it.equals('application') ) { defaultValue="Ready to cast" }

        returnValues.add( trackData.optString(it, defaultValue) ?: defaultValue )
    }

    return returnValues
}

def removeTrackData(keys) {
    JSONObject trackData = new JSONObject( device.currentValue("trackData") ?: "{}" )
    keys.each{
        if( trackData.has( it ) ) {
            if( it.equals('preset') ) {
                logger('debug', 'removeTrackData, resetPresetName for: ' + getTrackData(['preset'])[0])
                parsePresets()
            }
            logger('debug', "removeTrackData, removing key: "+it+", value: "+trackData.get(it))
            trackData.remove(it)
        }
    }
    sendEvent(name: "trackData", value: trackData, displayed:false)
}

// GOOGLE CAST
def setMediaPlayback(mediaType, mediaUrl, mediaStreamType, mediaTitle, mediaSubtitle, mediaImageUrl) {
    apiCall("/playMedia", true, '[ { "contentType":"'+mediaType+'", "mediaUrl":"'+mediaUrl+'", "mediaStreamType":"'+mediaStreamType+'", "mediaTitle":"'+mediaTitle+'", "mediaSubtitle":"'+mediaSubtitle+'", "mediaImageUrl":"'+mediaImageUrl+'" } ]')
}

def setMediaPlaybacks(def data) {
    apiCall("/playMedia", true, data)
}

// NETWORKING STUFF
def apiCall(String path, def dev, def media=null) {
    if (dev) {
        path = '/device/' + device.deviceNetworkId + path
    }
    if ( path.contains('subscribe') ) {
        def hub = location.hubs[0]
        path = path + '/' + hub.localIP + ':' + hub.localSrvPortTCP
    }
    if (media) {
        sendHttpPost(getDataValue('apiHost'), path, media)
        return
    }
    sendHttpRequest(getDataValue('apiHost'), path)
}

def sendHttpRequest(String host, String path, def defaultCallback=hubResponseReceived) {
    logger('debug', "Executing 'sendHttpRequest' host: "+host+" path: "+path)
    sendHubCommand(new hubitat.device.HubAction("""GET ${path} HTTP/1.1\r\nHOST: $host\r\n\r\n""", hubitat.device.Protocol.LAN, host, [callback: defaultCallback]))
}

def sendHttpPost(String host, String path, def data) {
    logger('debug', "Executing 'sendHttpPost' host: "+host+" path: "+path+" data: "+data+" data.length():"+data.length()+1)
    def ha = new hubitat.device.HubAction("""POST ${path} HTTP/1.1\r\nHOST: $host\r\nContent-Type: application/json\r\nContent-Length:${data.length()+1}\r\n\r\n ${data}""", hubitat.device.Protocol.LAN, host, [callback: hubResponseReceived])
    logger('debug', "HubAction: "+ha)
    sendHubCommand(ha)
}

void hubResponseReceived(hubitat.device.HubResponse hubResponse) {
    parse(hubResponse.description)
}

// HELPERS
def getTimeStamp() {
    Date now = new Date();
    def timeStamp = (long)(now.getTime()/1000)
    logger('info', "Timestamp generated: "+timeStamp)
    return timeStamp;
}

def urlEncode(String) {
    return java.net.URLEncoder.encode(String, "UTF-8")
}

def selectableAction(action) {
    if( action.equals("Play") ) { play() }
    if( action.equals("Pause") ) { pause() }
    if( action.equals("Stop") ) { stop() }
    if( action.equals("Play preset 1") ) { playPreset(1) }
    if( action.equals("Play preset 2") ) { playPreset(2) }
    if( action.equals("Play preset 3") ) { playPreset(3) }
    if( action.equals("Play preset 4") ) { playPreset(4) }
    if( action.equals("Play preset 5") ) { playPreset(5) }
    if( action.equals("Play preset 6") ) { playPreset(6) }
    if( action.equals("Next preset") ) { nextPreset() }
    if( action.equals("Previous preset") ) { previousPreset() }
}

//UPDATE
def getThisVersion() {
    return "1.0.0"
}

def getLatestVersion() {
    try {
        httpGet([uri: "https://raw.githubusercontent.com/vervallsweg/smartthings/master/devicetypes/vervallsweg/cast-web.src/version.json"]) { resp ->
            logger('debug', "response status: ${resp.status}")
            String data = "${resp.getData()}"
            logger('debug', "data: ${data}")

            if(resp.status==200 && data!=null) {
                return parseJson(data)
            } else {
                return null
            }
        }
    } catch (e) {
        logger('error', "something went wrong: $e")
        return null
    }
}

def checkForUpdate() {
    def latestVersion = getLatestVersion()
    if (latestVersion == null) {
        logger('error', "Couldn't check for new version, thisVersion: " + getThisVersion())
        sendEvent(name: "updateStatus", value: ("Version "+getThisVersion() + "\n Error getting latest version \n"), displayed: false)
        return null
    } else {
        logger('info', "checkForUpdate thisVersion: " + getThisVersion() + ", latestVersion: " + getLatestVersion().version)
        sendEvent(name: "updateStatus", value: ("Current: "+getThisVersion() + "\nLatest: " + getLatestVersion().version), displayed: false)
    }
}

//DEBUGGING
def logger(level, message) {
    def logLevel=1
    if(settings.configLoglevel) {
        logLevel = settings.configLoglevel.toInteger() ?: 0
    }
    if(level=="error"&&logLevel>0) {
        log.error message
    }
    if(level=="warn"&&logLevel>1) {
        log.warn message
    }
    if(level=="info"&&logLevel>2) {
        log.info message
    }
    if(level=="debug"&&logLevel>3) {
        log.debug message
    }
}
