-- Rejected redemptions spend nothing; earlier code stored the reward's price.
UPDATE reward_redemptions SET points_spent = 0 WHERE status = 'REJECTED' AND points_spent <> 0;
