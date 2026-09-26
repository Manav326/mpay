export const metadata = {
  title: "Delete Your mPay Account | mPay",
  description: "Request deletion of your mPay account and associated personal data.",
};

export default function DeleteAccountPage() {
  return (
    <main className="privacy-page">
      <div className="privacy-shell">
        <header className="privacy-header">
          <a href="/" className="privacy-brand">
            <img src="/mpay-logo.png" alt="mPay" />
            <span>mPay</span>
          </a>
          <a href="/privacy-policy" className="privacy-back">Privacy Policy</a>
        </header>

        <article className="privacy-card">
          <div className="privacy-kicker">ACCOUNT &amp; DATA CONTROL</div>
          <h1>Delete your mPay account</h1>
          <p className="privacy-effective">mPay account deletion request</p>

          <div className="deletion-callout">
            <strong>Delete from the mPay app</strong>
            <p>
              While signed in, open <strong>Profile → Delete account</strong>,
              enter your current password, and type <strong>DELETE</strong> to
              confirm. The request is processed by mPay immediately when there
              are no outstanding wallet, payout, recharge, or rental obligations.
            </p>
            <p>
              Unable to access your account? Email
              <a href="mailto:customer-mpay@thinkwithsujeet.in"> customer-mpay@thinkwithsujeet.in</a>
              with the subject <strong>&quot;mPay Account Deletion Request&quot;</strong>
              and your registered mobile number.
            </p>
          </div>

          <h2>What to include</h2>
          <ol>
            <li>Your registered 10-digit mobile number.</li>
            <li>Your account email address, if one is registered with mPay.</li>
            <li>Write clearly that you want your mPay account and associated personal data deleted.</li>
          </ol>

          <h2>What will be deleted</h2>
          <p>
            Subject to applicable legal and regulatory requirements, we will
            delete or anonymise the personal data associated with your account,
            including profile information, authentication information, profile
            image, and account-related service records that are not required to
            be retained.
          </p>

          <h2>What may be retained</h2>
          <p>
            Certain transaction, payment, security, fraud-prevention, dispute,
            accounting, tax, or regulatory records may need to be retained for a
            limited period when required by law or legitimate compliance
            obligations. Where information must be retained, it will be
            restricted to the applicable purpose and retention period.
          </p>

          <h2>Verification and processing</h2>
          <p>
            We may verify that the request was made by the account holder before
            processing it. We will process valid deletion requests within a
            reasonable period and may contact you if additional information is
            required to complete verification.
          </p>

          <h2>Need help?</h2>
          <p>
            For privacy or account-deletion questions, contact
            <a href="mailto:customer-mpay@thinkwithsujeet.in"> customer-mpay@thinkwithsujeet.in</a>.
          </p>
        </article>
      </div>
    </main>
  );
}
