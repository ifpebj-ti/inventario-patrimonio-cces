package clp.inventory.model;

public enum TokenType {
    VERIFICATION("verificationToken"),
    RESETPASSWORD("resetPasswordToken");

    private final String description;

    private TokenType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
