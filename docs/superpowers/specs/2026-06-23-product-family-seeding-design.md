# Product Family Seeding Design

## Goal

Seed a conservative, reusable product master from the current receipt-item data and apply it to existing receipts without creating unsafe product merges.

## Decisions

- Product rules use the store name and `EXACT` match type whenever a receipt description is specific to a retailer or abbreviated.
- A product family represents the product or product line. A product variant is created only when the receipt text proves a size, unit, or package structure.
- Families receive a default category only when the product class is clear. Existing item categories are never overwritten by product assignment.
- Coupons, cancellations, payment lines, rounding differences, giveaways, and deposits are assigned `NO_PRODUCT`, so they do not appear in the product review queue or price comparison.
- Ambiguous descriptions, especially generic counter service lines and incomplete retailer codes, remain `NEEDS_REVIEW` rather than receiving guessed master data.

## Seed Scope

The migration seeds high-confidence families and rules found in recurring current receipt data across REWE, dm, EDEKA, ALDI, C&A, Jawoll, pharmacies, McDonald's, Landmarkt, Buesch, and the fuel station. Cross-store rules are reserved for unambiguous branded products; all abbreviated labels are store-specific.

Official retailer research confirms that dm sells Denkmit destilliertes Wasser in both 2-l and 5-l variants and Mivolis Meerwasser Nasenspray as a health product. These receive separate, size-safe variants and correct default categories.

## Data Flow

1. Flyway V26 and V27 seed product families, explicit variants, and store-specific rules.
2. The existing product-assignment service applies rules to active receipt items and logs every result.
3. Items without a safe rule remain in the product review queue.
4. Existing categories remain unchanged; default categories only enrich future uncategorized items.

## Verification

An isolated Flyway integration test verifies representative seed data and a real assignment run. Unit tests verify that cancellation and deposit lines are `NO_PRODUCT`. The full Maven verification suite then checks the migration with the existing product, parser, sync, and API tests.
