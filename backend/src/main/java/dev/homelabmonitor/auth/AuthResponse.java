package dev.homelabmonitor.auth;

record AuthResponse(boolean setupRequired, boolean authenticated, OwnerView owner) {
	record OwnerView(String email, String displayName) {
		static OwnerView from(OwnerPrincipal principal) {
			return new OwnerView(principal.email(), principal.displayName());
		}
	}
}
