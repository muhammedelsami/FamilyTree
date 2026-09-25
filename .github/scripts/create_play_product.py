"""
Creates the premium one-time product in Google Play and makes it purchasable.

The app asks Play for `familytree_premium` and, until that product exists and is active,
reports the store as unavailable. This does the Play Console step through the Android
Publisher API instead of by hand, so the product is defined by a reviewed file rather than
by whatever was clicked.

It is safe to run twice: a product that already exists is left as it is, except that a
purchase option still in draft is activated. Prices are never changed on an existing
product — repricing a live product is a decision, not a side effect of a rerun.

Environment:
  PLAY_SERVICE_ACCOUNT_JSON  service account key with access to the app in Play Console
  PACKAGE_NAME               e.g. com.familytrees.app
  PRODUCT_ID                 e.g. familytree_premium
  PRICE                      base price, tax exclusive, e.g. 4.99
  CURRENCY                   ISO 4217 code of PRICE, e.g. USD
  DRY_RUN                    "true" to print what would be created and stop
"""

import json
import os
import sys
from decimal import Decimal, InvalidOperation

import requests
from google.oauth2 import service_account
from google.auth.transport.requests import Request

API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"
PURCHASE_OPTION_ID = "lifetime"

# Titles are capped at 55 characters and descriptions at 200 by the API.
LISTINGS = [
    {
        "languageCode": "en-US",
        "title": "Premium: tree merging",
        "description": "Apply the updates from a shared copy of your family tree in one step, "
        "instead of comparing the two by hand. A one-time purchase.",
    },
    {
        "languageCode": "tr-TR",
        "title": "Premium: ağaç birleştirme",
        "description": "Soyağacının paylaşılan bir kopyasındaki güncellemeleri elle karşılaştırmak "
        "yerine tek adımda uygula. Tek seferlik satın alma.",
    },
    {
        "languageCode": "ar",
        "title": "النسخة المميزة: دمج الأشجار",
        "description": "طبّق تحديثات نسخة مشتركة من شجرة عائلتك بخطوة واحدة بدلًا من المقارنة "
        "اليدوية بين النسختين. عملية شراء لمرة واحدة.",
    },
]


def money(amount: str, currency: str) -> dict:
    try:
        value = Decimal(amount)
    except InvalidOperation:
        sys.exit(f"PRICE is not a number: {amount!r}")
    if value <= 0:
        sys.exit(f"PRICE must be positive: {amount!r}")
    units = int(value)
    nanos = int(((value - units) * Decimal(1_000_000_000)).to_integral_value())
    return {"currencyCode": currency.upper(), "units": str(units), "nanos": nanos}


def show(value: dict) -> str:
    return f"{Decimal(value.get('units', '0')) + Decimal(value.get('nanos', 0)) / Decimal(1_000_000_000)} {value['currencyCode']}"


class Play:
    def __init__(self, key: str, package: str):
        credentials = service_account.Credentials.from_service_account_info(
            json.loads(key),
            scopes=["https://www.googleapis.com/auth/androidpublisher"],
        )
        credentials.refresh(Request())
        self.session = requests.Session()
        self.session.headers["Authorization"] = f"Bearer {credentials.token}"
        self.base = f"{API}/{package}"

    def call(self, method: str, path: str, *, allow_404: bool = False, **kwargs):
        response = self.session.request(method, f"{self.base}/{path}", timeout=60, **kwargs)
        if allow_404 and response.status_code == 404:
            return None
        if not response.ok:
            # The API's own message is the useful part: a missing permission, an unlinked
            # payments profile or a malformed price all come back as a 4xx that says which.
            sys.exit(f"{method} {path} failed with HTTP {response.status_code}:\n{response.text}")
        return response.json() if response.content else {}


def activate(play: Play, package: str, product_id: str, dry_run: bool) -> None:
    if dry_run:
        print(f"[dry run] would activate purchase option '{PURCHASE_OPTION_ID}'")
        return
    play.call(
        "POST",
        f"oneTimeProducts/{product_id}/purchaseOptions:batchUpdateStates",
        json={
            "requests": [
                {
                    "activatePurchaseOptionRequest": {
                        "packageName": package,
                        "productId": product_id,
                        "purchaseOptionId": PURCHASE_OPTION_ID,
                    }
                }
            ]
        },
    )
    print(f"Activated purchase option '{PURCHASE_OPTION_ID}'.")


def main() -> None:
    package = os.environ["PACKAGE_NAME"]
    product_id = os.environ["PRODUCT_ID"]
    dry_run = os.environ.get("DRY_RUN", "true").lower() == "true"
    play = Play(os.environ["PLAY_SERVICE_ACCOUNT_JSON"], package)

    existing = play.call("GET", f"oneTimeProducts/{product_id}", allow_404=True)
    if existing is not None:
        print(f"'{product_id}' already exists; its prices are left untouched.")
        drafts = [
            option["purchaseOptionId"]
            for option in existing.get("purchaseOptions", [])
            if option.get("state") != "ACTIVE"
        ]
        if PURCHASE_OPTION_ID in drafts:
            activate(play, package, product_id, dry_run)
        for option in existing.get("purchaseOptions", []):
            print(f"  purchase option '{option['purchaseOptionId']}': {option.get('state')}")
        return

    base = money(os.environ["PRICE"], os.environ["CURRENCY"])
    converted = play.call("POST", "pricing:convertRegionPrices", json={"price": base})
    regions = converted["convertedRegionPrices"]
    other = converted["convertedOtherRegionsPrice"]
    regions_version = converted["regionVersion"]["version"]

    print(f"Base price {show(base)} converted to {len(regions)} regions (regions version {regions_version}).")
    for code in ("TR", "US", "SA", "AE", "EG", "DE", "GB"):
        if code in regions:
            print(f"  {code}: {show(regions[code]['price'])}")

    product = {
        "packageName": package,
        "productId": product_id,
        "listings": LISTINGS,
        "purchaseOptions": [
            {
                "purchaseOptionId": PURCHASE_OPTION_ID,
                # Legacy-compatible, because the app reads the price through
                # ProductDetails.oneTimePurchaseOfferDetails — the single-offer accessor that
                # only sees a purchase option marked this way.
                "buyOption": {"legacyCompatible": True, "multiQuantityEnabled": False},
                "regionalPricingAndAvailabilityConfigs": [
                    {"regionCode": code, "price": entry["price"], "availability": "AVAILABLE"}
                    for code, entry in sorted(regions.items())
                ],
                "newRegionsConfig": {
                    "usdPrice": other["usdPrice"],
                    "eurPrice": other["eurPrice"],
                    "availability": "AVAILABLE",
                },
            }
        ],
    }

    if dry_run:
        print("[dry run] would create:")
        print(json.dumps({**product, "purchaseOptions": "…"}, ensure_ascii=False, indent=2))
        activate(play, package, product_id, dry_run)
        return

    play.call(
        "PATCH",
        # Lower-case "onetimeproducts" is the path the API publishes for this one method;
        # every other one-time product method spells it "oneTimeProducts".
        f"onetimeproducts/{product_id}",
        params={
            "allowMissing": "true",
            "regionsVersion.version": regions_version,
            "updateMask": "listings,purchaseOptions",
        },
        json=product,
    )
    print(f"Created '{product_id}'.")
    activate(play, package, product_id, dry_run)

    created = play.call("GET", f"oneTimeProducts/{product_id}")
    for option in created.get("purchaseOptions", []):
        print(f"  purchase option '{option['purchaseOptionId']}': {option.get('state')}")


if __name__ == "__main__":
    main()
