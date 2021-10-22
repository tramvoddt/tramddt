package payments.momo.mservice.pay.processor.notallinone;

import payments.momo.mservice.pay.models.MoMoJson;
import payments.momo.mservice.pay.models.PayConfirmationRequest;
import payments.momo.mservice.pay.models.PayConfirmationResponse;
import payments.momo.mservice.shared.constants.Parameter;
import payments.momo.mservice.shared.exception.MoMoException;
import payments.momo.mservice.shared.sharedmodels.AbstractProcess;
import payments.momo.mservice.shared.sharedmodels.Environment;
import payments.momo.mservice.shared.sharedmodels.HttpResponse;
import payments.momo.mservice.shared.utils.Encoder;
import payments.momo.mservice.shared.utils.LogUtils;

public class PayConfirmation extends AbstractProcess<PayConfirmationRequest, PayConfirmationResponse> {

    public PayConfirmation(Environment environment) {
        super(environment);
    }

    public static PayConfirmationResponse process(Environment env, String partnerRefId, String requestType, String requestId, String momoTransId, String customerNumber, String description) throws Exception {
        try {
            PayConfirmation payConfirmation = new PayConfirmation(env);

            PayConfirmationRequest payConfirmationRequest = payConfirmation.createAppAppConfirmRequest(partnerRefId, requestType, requestId,
                    momoTransId, customerNumber, description);
            PayConfirmationResponse payConfirmationResponse = payConfirmation.execute(payConfirmationRequest);
            return payConfirmationResponse;
        } catch (Exception e) {
            System.out.println("[PayConfirmationProcess] "+ e);
        }
        return null;
    }

    @Override
    public PayConfirmationResponse execute(PayConfirmationRequest request) throws MoMoException {
        try {
            String payload = getGson().toJson(request, PayConfirmationRequest.class);

            HttpResponse response = execute.sendToMoMo(environment.getMomoEndpoint(), payload);
            if (response.getStatus() != 200) {
                throw new MoMoException("[PayConfirmationResponse] [" + request.getPartnerRefId() + "] -> Error API");
            }

            PayConfirmationResponse payConfirmationResponse = getGson().fromJson(response.getData(), PayConfirmationResponse.class);

            if (payConfirmationResponse.getStatus() == 0) {
                MoMoJson data = payConfirmationResponse.getData();

                String rawData = Parameter.AMOUNT + "=" + data.getAmount() +
                        "&" + Parameter.MOMO_TRANS_ID + "=" + data.getMomoTransId() +
                        "&" + Parameter.PARTNER_CODE + "=" + data.getPartnerCode() +
                        "&" + Parameter.PARTNER_REF_ID + "=" + data.getPartnerRefId();

                String signature = Encoder.signHmacSHA256(rawData, partnerInfo.getSecretKey());
                System.out.println("[PayConfirmationResponse] rawData: " + rawData + ", [Signature] -> " + signature + ", [MoMoSignature] -> " + payConfirmationResponse.getSignature());

                if (!signature.equals(payConfirmationResponse.getSignature())) {
                    throw new IllegalArgumentException("Wrong signature from MoMo side - please contact with us");
                }
            } else {
                System.out.println("[PayConfirmationResponse] -> Status: " + payConfirmationResponse.getStatus() + ", Message: " + payConfirmationResponse.getMessage());
            }
            return payConfirmationResponse;

        } catch (Exception e) {
            System.out.println("[PayConfirmationResponse] "+ e);
        }
        return null;
    }

    public PayConfirmationRequest createAppAppConfirmRequest(String partnerRefId, String requestType, String requestId, String momoTransId,
                                                             String customerNumber, String description) {

        try {
            String requestRawData = new StringBuilder()
                    .append(Parameter.PARTNER_CODE).append("=").append(partnerInfo.getPartnerCode()).append("&")
                    .append(Parameter.PARTNER_REF_ID).append("=").append(partnerRefId).append("&")
                    .append(Parameter.REQUEST_TYPE).append("=").append(requestType).append("&")
                    .append(Parameter.REQUEST_ID).append("=").append(requestId).append("&")
                    .append(Parameter.MOMO_TRANS_ID).append("=").append(momoTransId)
                    .toString();

            String signRequest = Encoder.signHmacSHA256(requestRawData, partnerInfo.getSecretKey());
            System.out.println("[PayConfirmationResponse] rawData: " + requestRawData + ", [Signature] -> " + signRequest);

            return new PayConfirmationRequest(partnerInfo.getPartnerCode(), partnerRefId, customerNumber, description, momoTransId, requestType,requestId, signRequest);

        } catch (Exception e) {
            System.out.println("[PayConfirmationRequest] "+ e);
        }

        return null;
    }


}
