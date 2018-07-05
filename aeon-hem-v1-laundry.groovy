/*
Custom Laundry monitor device for Aeon HEM V1
  originally written by Mike Maxwell for SmartThings

  modified by Dan Ogorchock to work with Hubitat

  modified by Douglas Krug - Added button 3 and 4 push to indicate washer is running. For washers, this allows cancellation
of rule actions to prevent false notifications with washers having a very tight running wattage range,
and therefore fluctuating between ON and OFF status many times during a wash cycle. This also allows button push 3 and 4
to trigger virtual switches so the status of the each machine can be used with google voice assistant for verbal response.

*/

metadata {
	definition (name: "Aeon HEM V1 Laundry DTH", namespace:	"MikeMaxwell", author: "Mike Maxwell")
	{
		capability "Configuration"
		capability "Switch"
        capability "Button"
        //capability "Energy Meter"
		capability "Actuator"
        capability "Pushable Button"
        capability "Holdable Button"
		capability "Sensor"

        attribute "washerWatts", "string"
        attribute "dryerWatts", "string"
        attribute "washerState", "string"
        attribute "dryerState", "string"

		fingerprint deviceId: "0x2101", inClusters: " 0x70,0x31,0x72,0x86,0x32,0x80,0x85,0x60"
	}

	preferences {
       	input name: "washerRW", type: "number", title: "Washer running watts:", description: "", required: true
        input name: "dryerRW", type: "number", title: "Dryer running watts:", description: "", required: true
    }
}

def parse(String description) {
    //log.trace "Parse received ${description}"
	def result = null
	def cmd = zwave.parse(description, [0x31: 1, 0x32: 1, 0x60: 3])
	if (cmd) {
		result = createEvent(zwaveEvent(cmd))
	}
	if (result) {
		//log.debug "Parse returned ${result?.descriptionText}"
		return result
	} else {
	}
}

def zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
	//log.info "mc3v cmd: ${cmd}"
	if (cmd.commandClass == 50) {
    	def encapsulatedCommand = cmd.encapsulatedCommand([0x30: 1, 0x31: 1])
        if (encapsulatedCommand) {
        	def scale = encapsulatedCommand.scale
        	def value = encapsulatedCommand.scaledMeterValue
            def source = cmd.sourceEndPoint
            def str = ""
            def name = ""
        	if (scale == 2 ){ //watts
            	str = "watts"
                if (source == 1){
                	name = "washerWatts"
                    if (value.toInteger() >= settings.washerRW.toInteger()){
                    	//washer is on
                        //if (state.washerIsRunning == false){
                        if (device.currentValue("washerState") == "off"){
                        	//washer is running
                            sendEvent(name: "pushed", value: "3", descriptionText: "Washer is running.", isStateChange: true)
                        }
                        sendEvent(name: "washerState", value: "on", displayed: true)
                        state.washerIsRunning = true
                    } else {
                    	//washer is off
                        //if (state.washerIsRunning == true){
                        if (device.currentValue("washerState") == "on"){
                        	//button event
                            sendEvent(name: "pushed", value: "1", descriptionText: "Washer has finished.", isStateChange: true)
                        }
                        state.washerIsRunning = false
                        sendEvent(name: "washerState", value: "off", displayed: true)
                    }
                } else {
                	name = "dryerWatts"
                    if (value.toInteger() >= settings.dryerRW.toInteger()){
                    	//dryer is on
                        //if (state.dryerIsRunning == false){
                        if (device.currentValue("dryerState") == "off"){
                            //dryer is running
                            sendEvent(name: "pushed", value: "4", descriptionText: "Dryer is running.", isStateChange: true)
                        }
                        sendEvent(name: "dryerState", value: "on", displayed: true)
                        state.dryerIsRunning = true
                    } else {
                    	//dryer is off
                        //if (state.dryerIsRunning == true){
                        if (device.currentValue("dryerState") == "on"){
                        	//button event
                            sendEvent(name: "pushed", value: "2", descriptionText: "Dryer has finished.", isStateChange: true)
                        }
                        state.dryerIsRunning = false
                        sendEvent(name: "dryerState", value: "off", displayed: true)
                    }
                }
                if (state.washerIsRunning || state.dryerIsRunning){
                	sendEvent(name: "switch", value: "on", descriptionText: "Laundry has started...", displayed: true)
                } else {
                	sendEvent(name: "switch", value: "off", displayed: false)
                }
                //log.debug "mc3v- name: ${name}, value: ${value}, unit: ${str}"
            	return [name: name, value: value.toInteger(), unit: str, displayed: false]
            }
        }
    }
}

def zwaveEvent(hubitat.zwave.Command cmd) {
	// Handles all Z-Wave commands we aren't interested in
    //log.debug "Unhandled event ${cmd}"
	[:]
}

def configure() {
	log.debug "configure()"
    initialize()
	def cmd = delayBetween([
    	//zwave.configurationV1.configurationSet(parameterNumber: 100, size: 4, scaledConfigurationValue:1).format(),	//reset if not 0
        //zwave.configurationV1.configurationSet(parameterNumber: 110, size: 4, scaledConfigurationValue: 1).format(),	//reset if not 0

    	zwave.configurationV1.configurationSet(parameterNumber: 1, size: 2, scaledConfigurationValue: 120).format(),		// assumed voltage
		zwave.configurationV1.configurationSet(parameterNumber: 3, size: 1, scaledConfigurationValue: 0).format(),			// Disable (=0) selective reporting
		zwave.configurationV1.configurationSet(parameterNumber: 9, size: 1, scaledConfigurationValue: 10).format(),			// Or by 10% (L1)
      	zwave.configurationV1.configurationSet(parameterNumber: 10, size: 1, scaledConfigurationValue: 10).format(),		// Or by 10% (L2)
		zwave.configurationV1.configurationSet(parameterNumber: 20, size: 1, scaledConfigurationValue: 1).format(),			//usb = 1
		zwave.configurationV1.configurationSet(parameterNumber: 101, size: 4, scaledConfigurationValue: 6912).format(),
		zwave.configurationV1.configurationSet(parameterNumber: 111, size: 4, scaledConfigurationValue: 30).format() 		// Every 30 seconds
	], 2000)

	return cmd
}

def installed() {
	configure()
}

def updated() {
	configure()
}

def initialize() {
	sendEvent(name: "numberOfButtons", value: 4)
}

def push(btnNumber) {
    //log.debug btnNumber
    def desc = bthNumber==1?"Washer has finished":"Dryer has finished"
    sendEvent(name: "pushed", value: btnNumber, descriptionText: desc, isStateChange: true)
}
