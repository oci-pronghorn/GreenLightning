package com.ociweb.gl.json;

import com.ociweb.json.JSONExtractor;
import com.ociweb.json.JSONExtractorCompleted;
import com.ociweb.json.JSONType;
import com.ociweb.json.appendable.AppendableByteWriter;
import com.ociweb.json.encode.JSONRenderer;
import com.ociweb.json.encode.function.ToLongFunction;
import com.ociweb.json.encode.function.ToStringFunction;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.ChannelWriter;
import com.ociweb.pronghorn.util.parse.JSONReader;

public class RestResponse {
	private int status = 0;
    private final StringBuilder message = new StringBuilder();
    private final StringBuilder body = new StringBuilder();
    //private List<LogInfo> logInfo;

    private static ToLongFunction<RestResponse> statusFun = new  ToLongFunction<RestResponse>() {

		@Override
		public long applyAsLong(RestResponse value) {
			return value.getStatus();
		}
    	
    };
	private static ToStringFunction<RestResponse> statusMessage = new ToStringFunction<RestResponse>() {

		@Override
		public CharSequence applyAsString(RestResponse value) {
			return value.getMessage();
		}
		
	};
	private static ToStringFunction<RestResponse> statusBody = new ToStringFunction<RestResponse>() {

		@Override
		public CharSequence applyAsString(RestResponse value) {
			return value.getBody();
		}
		
	};
	
    private static final JSONRenderer<RestResponse> jsonRenderer = new JSONRenderer<RestResponse>()
            .beginObject()
            .integer("status", statusFun)
            .string("message", statusMessage)
            .string("body", statusBody)
            //.beginObject("logInfo")
            //.endObject()
            .endObject();

    public static final JSONExtractorCompleted jsonExtractor = new JSONExtractor()
            .newPath(JSONType.TypeInteger).key("status").completePath("status")
            .newPath(JSONType.TypeString).key("message").completePath("message")
            .newPath(JSONType.TypeString).key("body").completePath("body");

    public void reset() {
        status = 0;
        message.setLength(0);
        this.message.setLength(0);
        body.setLength(0);
    }

    public void setStatusMessage(OicStatusMessages oicStatusMessage) {
        this.status = oicStatusMessage.getStatusCode();
        this.message.append(oicStatusMessage.getStatusMessage());
    }

    public int getStatus() { return status; }

    public String getMessage() {
        return message.toString();
    }

    public String getBody() {
        return body.toString();
    }

    public void setBody(String body) {
        this.body.append(body);
    }

    // public void setLogInfo(List<LogInfo> logInfo) {
    //     this.logInfo = logInfo;
    //}

    public void writeExternal(ChannelWriter out) {
        out.writeInt(status);
        out.writeUTF(message);
        out.writeUTF(body);
    }

    public void readExternal(ChannelReader in) {
        status = in.readInt();
        in.readUTF(message);
        in.readUTF(body);
    }

    public static JSONReader createReader() {
        return jsonExtractor.reader();
    }

    public boolean readFromJSON(JSONReader jsonReader, ChannelReader reader) {
        status = (int)jsonReader.getLong("status".getBytes(), reader);
        jsonReader.getText("message".getBytes(), reader, message);
        jsonReader.getText("body".getBytes(), reader, body);
        return true;
    }

    public void writeToJSON(AppendableByteWriter writer) {
        jsonRenderer.render(writer, this);
    }

    public enum OicStatusMessages {
        // success
        SVC_SUCCESS(200, "Success"),
        // failure
        SVC_FAILURE(500, "Transaction Failed"),
        // bad request
        BAD_REQUEST(400, "Bad Request"),
        // supply stale data
        SUPP_FEED_OUT_OF_SEQ(900, "Out of sequence transaction found.  Message rejected."),
        // mandatory parameters missing
        UNKNOWN_ERROR(0, "Unkown Error Occured"),
        // mandatory parameters missing
        MISSING_INP_ATTRIB(1, "Mandatory parameters missing"),
        // invalid timestamp
        INVLID_TIMESTAMP(2, "Invalid Timestamp format, expected format:(yyyy-MM-dd HH:mm:ss.SSSS z)."),
        // only pick and ship are allowed
        INVALID_CHANNEL(3, "Only 'PICK' and 'SHP' channels allowed"),
        // force reserve should contain only enum values
        INVALID_FORCE_RESERVE(4, "Only 'Y' and 'N' are allowed for ForceReserve Flag"),
        // Uom can only be each
        INVALID_UOM(5, "Only 'EACH' uom allowed"),
        // Negative quantity
        NEGATIVE_QTY(6, " Negative qty not allowed"),
        // Negative cancel qty
        NEGATIVE_CANCEL_QTY(7, "Negative cancel qty not allowed"),
        // both cancel and and qty, given in the request
        BOTH_CANCEL_AND_RESERVATION(8, "Both cancellation and reservation not allowed in same request"),
        // reservation shortage found
        RESERVE_SHORTAGE_QTY(350, "Shortage detected, reservation not done"),
        // cancellation came before reservation
        RESERVE_NOT_FOUND(11, "Reservation not found"),
        // to be deleted
        RESERVE_ROLLBACK_SUCCESS(12, "Problems found, Reservation rollback success"),
        // to be deleted
        RESERVE_ROLLBACK_FAILED(13, "Reservation rollback failed"),
        // reservation lines increased
        RESERVE_LINE_INCREASE(14, "Adding new lines in Change/Cancel Reservation is not allowed"),
        // Qty increased
        RESERVE_QTY_INCREASE(15, "Increasing reservation qty is only allowed when forceReserve is passed as Y"),
        // ttl non zero is not allowed plan b
        PLANB_TTL(16, "Only zero ttl is allowed"),
        // storeId should be null
        PLANB_STORE_NOT_NULL(17, "Store level reservations are not allowed"),
        // channel should be shp
        PLANB_CHANNEL_NOT_SHP(18, "Only SHP reservations are allowed"),
        // lineids repeated
        REPEATED_LINE_ID(19, "Every line in the input must have a different lineID"),
        // multiple reservation Ids
        PLANB_MULTIPLE_RESERVE_IDS(20, "Only one reservationID allowed in input"),
        // reservation with qty zero
        CREATE_RESERVE_ZERO_QTY(21, "Reservation with zero quantity not allowed"),
        // if line id is null qty can't be increased
        RESERVE_QTY_INCREASE_LINEID_NULL(23, "lineID must be passed when increasing reservation qty"),
        // Same reservation request came without change
        RESERVATION_REQUEST_REPEATED(24, "No Change detected in reservation to process"),
        // Same reservation request came without change
        RESERVATION_CANCEL_QTY_INCREASE(25, "Cancel quantity should be less than reserved qty"),
        // check availability line request with zero or negative qty.
        NEGATIVE_ZERO_QTY(26, "Quantity cannot be zero or negative for check availability request."),
        // check availability line request should have pickUpStoreID if shipMethod is pick/boss.
        MISSING_PICKUPSTOREID(27, "Check availability line request should have pickUpStoreID when shipMethod is pick/boss"),
        //shipMethod type should have either of these values "SHP,BOSS,PICK"
        INVALID_SHIPMETHOD(28,"Ship method in request should either be SHP/BOSS/PICK");

        private final int statusCode;
        private final String statusMessage;

        OicStatusMessages(int statusCode, String statusMessage) {
            this.statusCode = statusCode;
            this.statusMessage = statusMessage;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getStatusMessage() {
            return statusMessage;
        }

        public static OicStatusMessages getOicStatusMessages(String msg) {
            for (OicStatusMessages enumName : OicStatusMessages.values()) {
                if (enumName.getStatusMessage().equals(msg)) {
                    return enumName;
                }
            }
            return null;
        }
    }
}