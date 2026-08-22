export interface Coordinates {
  latitude: number;
  longitude: number;
}

/** Wraps the browser Geolocation API in a promise with a friendlier error message. */
export function getCurrentCoordinates(): Promise<Coordinates> {
  return new Promise((resolve, reject) => {
    if (!("geolocation" in navigator)) {
      reject(new Error("This device/browser doesn't support location — can't verify your position."));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (position) => resolve({ latitude: position.coords.latitude, longitude: position.coords.longitude }),
      (error) => reject(new Error(describeGeoError(error))),
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 },
    );
  });
}

function describeGeoError(error: GeolocationPositionError): string {
  switch (error.code) {
    case error.PERMISSION_DENIED:
      return "Location access was denied — allow location for this site and try again.";
    case error.POSITION_UNAVAILABLE:
      return "Couldn't determine your location — try again in the open.";
    case error.TIMEOUT:
      return "Getting your location took too long — try again.";
    default:
      return "Couldn't get your location.";
  }
}
