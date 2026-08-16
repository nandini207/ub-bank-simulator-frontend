# Union Bank Simulator — React UI

A React/Vite frontend for the supplied Spring Boot Union Bank simulator.

## Backend compatibility

This UI is wired to the backend exactly as supplied:

- `GET /control` — reads the existing Thymeleaf control page and extracts current settings/transactions.
- `POST /control` — saves simulator behavior settings.
- `GET /corp/SHPREQ?PGID=...&QS=...` — submits a real payment request to the supplied backend, which decrypts/parses/saves the transaction. The React UI then reads the generated Thymeleaf HTML and displays the transaction.
- `POST /corp/pay` — triggers SUCCESS / FAILURE / PENDING / CANCEL.
- `GET /corp/SHPVER` remains a server-to-server endpoint used by BillDesk and is not called from the UI.

## Run

1. Start the Spring Boot backend in STS. It must be available at `http://localhost:8080`.
2. In this folder run:
   ```bash
   npm install
   npm run dev
   ```
3. Open:
   `http://localhost:5173`

## Important architecture note

The supplied backend is not a JSON REST API for the control panel/payment screen; it returns Thymeleaf HTML for `/control` and `/corp/SHPREQ`.

To avoid changing your backend contract, this frontend uses the Vite development proxy and parses those existing HTML responses. This keeps the backend untouched while still wiring the React UI to the real endpoints.

For a production-grade React integration, the next backend improvement would be small JSON endpoints such as `/api/control`, `/api/transactions`, and `/api/payment-request`. That is not required for this UI to run.

## Payment request

The **Payment Request** screen accepts either:

- a complete `/corp/SHPREQ?PGID=...&QS=...` URL, or
- separate `PGID` and `QS` values.

The backend is actually called when you click **Load payment request**, so the transaction is saved in the backend repository before the UI displays it.

## No database

The supplied backend stores transactions in a `ConcurrentHashMap`, so the transaction history is session-only and disappears when the backend restarts. The React dashboard reflects that behavior.
