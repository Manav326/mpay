export const metadata = {
  title: "Privacy Policy | mPay",
  description: "Privacy Policy for the mPay mobile application and services.",
};

export default function PrivacyPolicyPage() {
  return (
    <main className="privacy-page">
      <div className="privacy-shell">
        <header className="privacy-header">
          <a href="/" className="privacy-brand">
            <img src="/mpay-logo.png" alt="mPay" />
            <span>mPay</span>
          </a>
          <a href="/" className="privacy-back">Back to mPay</a>
        </header>

        <article className="privacy-card">
          <div className="privacy-kicker">LEGAL &amp; PRIVACY</div>
          <h1>Privacy Policy</h1>
          <p className="privacy-effective">Effective date: September 26, 2026</p>
          <div className="deletion-callout">
            <strong>Account deletion</strong>
            <p>
              To request deletion of your mPay account and associated personal data,
              visit <a href="/delete-account">mPay Account Deletion</a> or email
              <a href="mailto:customer-mpay@thinkwithsujeet.in"> customer-mpay@thinkwithsujeet.in</a>.
            </p>
          </div>

          <p>
            This Privacy Policy explains how mPay collects, uses, stores, and shares
            information when you use the mPay mobile application, website, wallet,
            mobile recharge services, car-rental services, and related features.
          </p>

          <h2>1. Information we collect</h2>
          <p>Depending on the features you use, mPay may collect:</p>
          <ul>
            <li><strong>Account information:</strong> mobile number, name, email address, password credentials, account role, and account status.</li>
            <li><strong>Profile information:</strong> profile photograph when you choose to upload one.</li>
            <li><strong>Wallet and transaction information:</strong> wallet balance, wallet ledger entries, payment order details, recharge transactions, transaction status, provider references, and related timestamps.</li>
            <li><strong>Recharge information:</strong> the mobile number being recharged, recipient name when provided, operator, circle, plan information, and recharge amount.</li>
            <li><strong>Withdrawal information:</strong> withdrawal amount, UPI ID, provider reference, status, and related transaction records.</li>
            <li><strong>Car-rental information:</strong> booking details, pickup and drop locations, booking dates and times, vehicle and driver details, and vendor information where applicable.</li>
            <li><strong>Location information:</strong> address, place identifiers, and approximate geographic coordinates associated with rental pickup and drop locations when you provide them. mPay does not request Android device location permission in the current app.</li>
            <li><strong>Security and support information:</strong> authentication and password-reset records, including OTP verification metadata used to protect accounts.</li>
          </ul>

          <h2>2. How we use information</h2>
          <p>We use information to:</p>
          <ul>
            <li>create and secure accounts and authenticate users;</li>
            <li>process wallet funding, withdrawals, recharges, rentals, and other requested services;</li>
            <li>maintain transaction history, balances, receipts, and service status;</li>
            <li>provide customer, vendor, and operational support;</li>
            <li>detect misuse, prevent fraud, protect the platform, and maintain service reliability; and</li>
            <li>comply with legal, regulatory, accounting, and security obligations.</li>
          </ul>

          <h2>3. Payment and service providers</h2>
          <p>
            mPay may share the information necessary to complete a requested service
            with the relevant service provider. Depending on the feature and
            configuration, these providers may include Razorpay or PayU for payment
            processing, Twilio for OTP delivery and verification, and Google Maps or
            Google Places for map and place-related functionality.
          </p>
          <p>
            Payment providers may collect payment information directly during
            checkout. mPay stores payment order, transaction, and provider-reference
            information needed to reconcile and support payments; mPay does not
            intentionally store your card PIN, UPI PIN, or full card credentials.
          </p>

          <h2>4. Sharing of information</h2>
          <p>
            We do not sell personal information. We may disclose information to
            service providers that process data on our behalf, to other parties when
            required to complete a service you request, when required by law or a
            lawful government request, or when necessary to protect users, the
            platform, or the rights and safety of others.
          </p>

          <h2>5. Data retention and security</h2>
          <p>
            We retain information for as long as reasonably necessary to operate your
            account and provide requested services, maintain financial and transaction
            records, resolve disputes, prevent abuse, and satisfy legal or regulatory
            requirements. We use access controls, authenticated APIs, password
            hashing, encrypted transport, and other reasonable safeguards designed to
            protect information. No method of storage or transmission can be
            guaranteed to be completely secure.
          </p>

          <h2>6. Your choices and requests</h2>
          <p>
            You may review or update available profile information from within mPay.
            You may also request correction or deletion of personal information,
            subject to records that we are required to retain for legal, financial,
            fraud-prevention, or dispute-resolution purposes.
          </p>
          <p>
            For privacy or data-related requests, please email
            <a href="mailto:customer-mpay@thinkwithsujeet.in">customer-mpay@thinkwithsujeet.in</a>. We may need to verify your identity
            before completing a request.
          </p>

          <h2>7. Children&apos;s privacy</h2>
          <p>
            mPay is not directed to children under 13, and we do not knowingly
            collect personal information from children under 13.
          </p>

          <h2>8. Changes to this policy</h2>
          <p>
            We may update this Privacy Policy when our services, technology, or legal
            requirements change. The updated version will be published on this page
            with a revised effective date.
          </p>

          <h2>9. Contact</h2>
          <p>
            For privacy questions or requests, email
            <a href="mailto:customer-mpay@thinkwithsujeet.in">customer-mpay@thinkwithsujeet.in</a>.
          </p>
        </article>
      </div>
    </main>
  );
}
